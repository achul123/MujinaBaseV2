package io.github.lefraudeur;

import io.github.lefraudeur.internal.Canceler;
import io.github.lefraudeur.internal.EventHandler;
import io.github.lefraudeur.internal.Thrower;
import net.minecraft.block.Block;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.world.IBlockAccess;

import static io.github.lefraudeur.internal.patcher.MethodModifier.Type.*;

public class TestClass
{
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
        canceler.cancel = true;
        if (thisBlock instanceof net.minecraft.block.BlockOre)
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
        return returnValue + " (Mujina Boosted)";
    }

    @EventHandler(type=ON_LDC_CONSTANT,
            targetClass = "net/minecraft/client/renderer/EntityRenderer",
            targetMethodName = "getMouseOver",
            targetMethodDescriptor = "(F)V")
    public static Object getMouseOverVar4(Object value)
    {
        if (value instanceof Double && (Double)value == 3.0D)
            return 4.0D;
        return value;
    }


}
