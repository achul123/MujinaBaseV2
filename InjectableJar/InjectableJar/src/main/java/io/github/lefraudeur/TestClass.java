package io.github.lefraudeur;

import io.github.lefraudeur.internal.Canceler;
import io.github.lefraudeur.internal.EventHandler;
import io.github.lefraudeur.internal.Thrower;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.world.IBlockAccess;
import org.lwjgl.input.Keyboard;

import static io.github.lefraudeur.internal.patcher.MethodModifier.Type.*;

public class TestClass
{
    private static boolean xrayEnabled = false;

    @EventHandler(type=ON_ENTRY,
            targetClass = "net/minecraft/client/entity/EntityClientPlayerMP",
            targetMethodName = "sendChatMessage",
            targetMethodDescriptor = "(Ljava/lang/String;)V",
            targetMethodIsStatic = false)
    public static void sendChatMessage(Canceler canceler, EntityClientPlayerMP player, String message)
    {
        System.out.println("sendChatMessage on entry succeeded");
    }

    @EventHandler(type=ON_RETURN_THROW,
            targetClass = "net/minecraft/client/entity/EntityClientPlayerMP",
            targetMethodName = "sendChatMessage",
            targetMethodDescriptor = "(Ljava/lang/String;)V")
    public static void sendChatMessage(Thrower thrower, EntityClientPlayerMP player, String message)
    {
        System.out.println("sendChatMessage on return succeeded");
    }

    @EventHandler(type=ON_ENTRY,
            targetClass = "net/minecraft/block/Block",
            targetMethodName = "shouldSideBeRendered",
            targetMethodDescriptor = "(Lnet/minecraft/world/IBlockAccess;IIII)Z")
    public static boolean shouldSideBeRendered(Canceler canceler, Block thisBlock, IBlockAccess p_149646_1_, int p_149646_2_, int p_149646_3_, int p_149646_4_, int p_149646_5_)
    {
        if (!xrayEnabled) return false;

        canceler.cancel = true;
        if (thisBlock.getUnlocalizedName().contains("ore"))
            return true;
        return false;
    }

    @EventHandler(type=ON_RETURN_THROW,
            targetClass = "net/minecraft/client/ClientBrandRetriever",
            targetMethodName = "getClientModName",
            targetMethodDescriptor = "()Ljava/lang/String;",
            targetMethodIsStatic = true)
    public static String getClientModName(String returnValue, Thrower thrower)
    {
        return returnValue + "Mujina Boosted";
    }

    @EventHandler(type=ON_LDC_CONSTANT,
            targetClass = "net/minecraft/client/renderer/EntityRenderer",
            targetMethodName = "getMouseOver",
            targetMethodDescriptor = "(F)V")
    public static Object getMouseOverVar4(Object value)
    {
        // reach
        if (value instanceof Double && (Double)value == 3.0D)
            return 4.0D;
        return value;
    }

    @EventHandler(type=ON_ENTRY,
            targetClass = "net/minecraft/client/Minecraft",
            targetMethodName = "runTick",
            targetMethodDescriptor = "()V",
            targetMethodIsStatic = false)
    public static void runTick(Canceler canceler, Minecraft this_minecraft)
    {
    }


    @EventHandler(type=ON_RETURN_THROW,
            targetClass = "org/lwjgl/input/Keyboard",
            targetMethodName = "next",
            targetMethodDescriptor = "()Z",
            targetMethodIsStatic = true)
    public static boolean Keyboard_next(boolean returnValue, Thrower thrower)
    {
        if (!returnValue) return false;
        if (Keyboard.getEventKeyState() && Keyboard.getEventKey() == Keyboard.KEY_X)
        {
            xrayEnabled = !xrayEnabled;
            Minecraft.getMinecraft().renderGlobal.loadRenderers();
        }
        return true;
    }
}
