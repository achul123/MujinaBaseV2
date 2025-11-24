#ifdef _WIN32
    #include <Windows.h>
#elif defined(__linux__)
    #include <X11/Xlib.h>
    #include <X11/Xutil.h>
#endif

#include "meta_jni.hpp"
#include "mappings.hpp"
#include "InjectableJar.jar.hpp"
#include "MemoryJarClassLoader.class.hpp"
#include "jvmti/jvmti.hpp"
#include "logger/logger.hpp"
#include "transformer/transformer.hpp"
#include <thread>
#include <iostream>

#ifndef MINECRAFT_CLASS
# define MINECRAFT_CLASS "net/minecraft/client/Minecraft"
#endif


#ifdef __linux__
static Display* display = nullptr;
#endif

static bool is_uninject_key_pressed()
{
#ifdef _WIN32
    return GetAsyncKeyState(VK_END);
#elif __linux__
    static KeyCode keycode = XKeysymToKeycode(display, XK_End);

    char key_states[32] = { '\0' };
    XQueryKeymap(display, key_states);

    // <<3 same as /8 (logic 2^3 = 8) and &7 same as %8 (idk y)
    return (key_states[keycode << 3] & (1 << (keycode & 7)));
#endif
}

static void mainFrame(const jvmti& jvmti_instance)
{
    jni::frame frame{}; // every local ref follow this frame object lifetime


    maps::Class minecraft_class = jvmti_instance.find_loaded_class(MINECRAFT_CLASS);
    if (!minecraft_class)
        return logger::error("failed to get minecraft_class");

    maps::ClassLoader minecraft_classloader = jvmti_instance.get_class_ClassLoader(minecraft_class);
    if (!minecraft_classloader)
        return logger::error("failed to get minecraft_classloader");

    // create a new classloader, and make it define the MemoryJarClassLoader class
    maps::SecureClassLoader secureClassLoader = maps::SecureClassLoader::new_object(&maps::SecureClassLoader::constructor2, (maps::ClassLoader)minecraft_classloader);
    if (!secureClassLoader)
        return logger::error("failed to create secureClassLoader");

    jclass MemoryJarClassLoaderClass = jni::get_env()->DefineClass(maps::MemoryJarClassLoader::get_name(), secureClassLoader, (jbyte*)MemoryJarClassLoader_class.data(), MemoryJarClassLoader_class.size());
    if (!MemoryJarClassLoaderClass)
        return logger::error("failed to define MemoryJarClassLoader class");

    // tell MetaJni the MemoryJarClassLoader jclass it needs to use
    jni::jclass_cache<maps::MemoryJarClassLoader>::value = MemoryJarClassLoaderClass;



    // here we create the MemoryJarClassLoader that will define InjectableJar, you can replace this by a classic URLClassLoader to load from a file instead.
    // warning: MemoryJarClassLoader also changes the delegation model for the asm library, so you might have issues doing that 
    // (you could code a new ClassLoader that extends URLClassLoader to fix it)
    jni::array<jbyte> InjectableJarJbyteArray = jni::array<jbyte>::create(std::vector<jbyte>(InjectableJar_jar.begin(), InjectableJar_jar.end()));
    maps::MemoryJarClassLoader classLoader = maps::MemoryJarClassLoader::new_object(&maps::MemoryJarClassLoader::constructor, InjectableJarJbyteArray, (maps::ClassLoader)secureClassLoader);
    if (!classLoader)
        return logger::error("failed to create memoryJarClassLoader");

    // TODO: Make meta jni custom findClass that uses the classLoader we just created

    // metaJNI uses env->findClass to get the jclass, however our Jar isn't in SystemClassLoader search path
    jni::jclass_cache<maps::Main>::value = classLoader.loadClass(maps::String::create(jni::to_dot<maps::Main::get_name()>()));
    if (!jni::jclass_cache<maps::Main>::value)
        return logger::error("failed to find io.github.lefraudeur.Main");
    logger::log("loaded main class");

    // Setup the classLoader trick so that minecraft classes can access the cheat classes
    jni::jclass_cache<maps::EventClassLoader>::value = classLoader.loadClass(maps::String::create(jni::to_dot<maps::EventClassLoader::get_name()>()));
    if (!jni::jclass_cache<maps::EventClassLoader>::value)
        return logger::error("failed to find io.github.lefraudeur.internal.EventClassLoader");

    maps::EventClassLoader eventClassLoader = maps::EventClassLoader::new_object(&maps::EventClassLoader::constructor, minecraft_classloader.parent.get(), classLoader);
    minecraft_classloader.parent = (maps::ClassLoader)eventClassLoader;

    // setup classLoader trick for the classLoader that defined org.lwjgl.input.Keyboard
    maps::ClassLoader lwjgl_classLoader = jvmti_instance.get_class_ClassLoader(minecraft_classloader.loadClass(maps::String::create("org.lwjgl.input.Keyboard")));
    maps::EventClassLoader eventClassLoader2 = maps::EventClassLoader::new_object(&maps::EventClassLoader::constructor, lwjgl_classLoader.parent.get(), classLoader);
    lwjgl_classLoader.parent = (maps::ClassLoader)eventClassLoader2;

    if (!transformer::init(jvmti_instance, classLoader))
    {
        // remove classLoader trick
        minecraft_classloader.parent = eventClassLoader.parent.get();
        lwjgl_classLoader.parent = eventClassLoader2.parent.get();
        return;
    }


    // we now call the onLoad method which should send hello in chat
    maps::Main Main{};
    Main.onLoad();

    while (!is_uninject_key_pressed())
    {
        std::this_thread::sleep_for(std::chrono::milliseconds(50));
    }

    Main.onUnload();

    transformer::shutdown(jvmti_instance);

    // remove classLoader trick
    minecraft_classloader.parent = eventClassLoader.parent.get();
    lwjgl_classLoader.parent = eventClassLoader2.parent.get();

}

static void app()
{

#ifdef _WIN32
    
#elif defined(__linux__)
    display = XOpenDisplay(NULL);
#endif
    jint result = 0;
    JavaVM* jvm = nullptr;
    result = JNI_GetCreatedJavaVMs(&jvm, 1, nullptr);
    if (result != JNI_OK || !jvm)
        return logger::error("failed to get JavaVM*");
    JNIEnv* env = nullptr;
    result = jvm->AttachCurrentThread((void**)&env, nullptr);
    if (result != JNI_OK || !env)
        return logger::error("failed to get JNIEnv*");
    if (!jni::init())
    {
        jvm->DetachCurrentThread();
        return;
    }
    jni::set_thread_env(env); //this is needed for every new thread that uses MetaJNI

    jvmti jvmti_instance{ jvm };
    if (jvmti_instance)
        mainFrame(jvmti_instance);


    jni::shutdown();
    jvm->DetachCurrentThread();

#ifdef _WIN32

#elif defined(__linux__)
    XCloseDisplay(display);
#endif
}

static void mainThread(void* dll)
{
    if (!logger::init())
        return;
    app();
    logger::log("unloaded");
    logger::shutdown();

#ifdef _WIN32
    FreeLibraryAndExitThread((HMODULE)dll, 0);
#endif
    return;
}

#ifdef _WIN32

BOOL WINAPI DllMain(
    HINSTANCE hinstDLL,  // handle to DLL module
    DWORD fdwReason,     // reason for calling function
    LPVOID lpvReserved)  // reserved
{
    // Perform actions based on the reason for calling.
    switch (fdwReason)
    {
    case DLL_PROCESS_ATTACH:
        // Initialize once for each new process.
        // Return FALSE to fail DLL load.
        CloseHandle(CreateThread(nullptr, 0, (LPTHREAD_START_ROUTINE)mainThread, hinstDLL, 0, 0));
        break;

    case DLL_THREAD_ATTACH:
        // Do thread-specific initialization.
        break;

    case DLL_THREAD_DETACH:
        // Do thread-specific cleanup.
        break;

    case DLL_PROCESS_DETACH:

        if (lpvReserved != nullptr)
        {
            break; // do not do cleanup if process termination scenario
        }

        // Perform any necessary cleanup.
        break;
    }
    return TRUE;  // Successful DLL_PROCESS_ATTACH.
}

#elif defined(__linux__)

void __attribute__((constructor)) onload_linux()
{
    pthread_t thread = 0U;
    pthread_create(&thread, nullptr, (void* (*)(void*))mainThread, nullptr);
    return;
}
void __attribute__((destructor)) onunload_linux()
{
    return;
}

#endif