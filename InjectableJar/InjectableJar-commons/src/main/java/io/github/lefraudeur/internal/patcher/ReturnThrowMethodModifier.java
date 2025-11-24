package io.github.lefraudeur.internal.patcher;

import io.github.lefraudeur.internal.Canceler;
import io.github.lefraudeur.internal.Thrower;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.AdviceAdapter;
import org.objectweb.asm.commons.Method;


public class ReturnThrowMethodModifier extends MethodModifier
{

    public ReturnThrowMethodModifier(MethodModifierInfo info)
    {
        super(Type.ON_RETURN_THROW, info);
    }

    @Override
    public String getNewInstanceCode()
    {
        return String.format("new ReturnThrowMethodModifier(%s)", this.info.getNewInstanceCode());
    }

    @Override
    public MethodVisitor getMethodVisitor(MethodVisitor forwardTo, int access, String name, String descriptor)
    {
        MethodVisitor methodVisitor = new AdviceAdapter(Opcodes.ASM9, forwardTo, access, name, descriptor)
        {
            @Override
            protected void onMethodExit(int opcode)
            {
                String ThrowerClassName = Thrower.class.getName().replace('.', '/');
                mv.visitTypeInsn(Opcodes.NEW, ThrowerClassName);
                mv.visitInsn(Opcodes.DUP);
                mv.visitInsn(Opcodes.DUP);
                int throwerVarIndex = availableVarIndex;
                availableVarIndex++;
                mv.visitVarInsn(Opcodes.ASTORE, throwerVarIndex);
                mv.visitMethodInsn(Opcodes.INVOKESPECIAL, ThrowerClassName, "<init>", "()V", false);

                if (opcode == Opcodes.ATHROW)
                {
                    mv.visitInsn(Opcodes.SWAP);
                    mv.visitFieldInsn(Opcodes.PUTFIELD, ThrowerClassName, "thrown", "Ljava/lang/Throwable;");

                    // now the stack should be empty

                    // push default return value, as it's not a return
                    switch (method.getReturnType().getSort())
                    {
                        case org.objectweb.asm.Type.BOOLEAN:
                        case org.objectweb.asm.Type.CHAR:
                        case org.objectweb.asm.Type.BYTE:
                        case org.objectweb.asm.Type.SHORT:
                        case org.objectweb.asm.Type.INT:
                            mv.visitInsn(Opcodes.ICONST_0);
                            break;
                        case org.objectweb.asm.Type.FLOAT:
                            mv.visitInsn(Opcodes.FCONST_0);
                            break;
                        case org.objectweb.asm.Type.LONG:
                            mv.visitInsn(Opcodes.LCONST_0);
                            break;
                        case org.objectweb.asm.Type.DOUBLE:
                            mv.visitInsn(Opcodes.DCONST_0);
                            break;
                        case org.objectweb.asm.Type.ARRAY:
                        case org.objectweb.asm.Type.OBJECT:
                            mv.visitInsn(Opcodes.ACONST_NULL);
                            break;
                        default:
                            throw new RuntimeException("incorrect return Type or not implemented");
                    }

                    mv.visitVarInsn(Opcodes.ALOAD, throwerVarIndex);
                }

                pushParametersAndCallEventHandler(mv);

                if (opcode == Opcodes.ATHROW)
                {
                    popReturnValueBasedOnReturnType(mv);
                    mv.visitVarInsn(Opcodes.ALOAD, throwerVarIndex);
                    mv.visitFieldInsn(Opcodes.GETFIELD, ThrowerClassName, "thrown", "Ljava/lang/Throwable;");
                }
            }
        };
        return this.new AvailableIndexMethodVisitor(Opcodes.ASM9, methodVisitor);
    }
}
