package simpletv.buildlogic;

import com.android.build.api.instrumentation.AsmClassVisitorFactory;
import com.android.build.api.instrumentation.ClassContext;
import com.android.build.api.instrumentation.ClassData;
import com.android.build.api.instrumentation.InstrumentationParameters;
import java.util.ArrayList;
import java.util.List;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public abstract class ReleaseStrings implements AsmClassVisitorFactory<ReleaseStrings.Parameters> {
    public interface Parameters extends InstrumentationParameters {
        @Input Property<String> getSigner();
    }

    private static final String VAULT = "io/mediacast/simpletv/protection/StringVault";
    private static final String STRING = "Ljava/lang/String;";

    @Override
    public boolean isInstrumentable(ClassData data) {
        String name = data.getClassName().replace('/', '.');
        return name.startsWith("io.mediacast.simpletv.")
                && !name.equals("io.mediacast.simpletv.StvApp")
                && !name.equals(VAULT.replace('/', '.'));
    }

    @Override
    public ClassVisitor createClassVisitor(ClassContext context, ClassVisitor next) {
        byte[] signer = EncodedLiteral.signerBytes(getParameters().get().getSigner().get());
        return new ClassVisitor(Opcodes.ASM9, next) {
            private final List<ConstantField> constants = new ArrayList<>();
            private String owner;
            private boolean hasInitializer;

            @Override
            public void visit(int version, int access, String name, String signature,
                              String superName, String[] interfaces) {
                owner = name;
                super.visit(version, access, name, signature, superName, interfaces);
            }

            @Override
            public FieldVisitor visitField(int access, String name, String descriptor,
                                           String signature, Object value) {
                if ((access & Opcodes.ACC_STATIC) != 0 && value instanceof String text && !text.isEmpty()) {
                    constants.add(new ConstantField(name, text));
                    value = null;
                }
                return super.visitField(access, name, descriptor, signature, value);
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor downstream = super.visitMethod(access, name, descriptor, signature, exceptions);
                boolean initializer = name.equals("<clinit>");
                hasInitializer |= initializer;
                return new MethodVisitor(Opcodes.ASM9, downstream) {
                    @Override
                    public void visitCode() {
                        super.visitCode();
                        if (initializer) initializeConstants(mv);
                    }

                    @Override
                    public void visitLdcInsn(Object value) {
                        if (value instanceof String text && !text.isEmpty()) {
                            emit(mv, text, owner + "." + name + descriptor, signer);
                        } else {
                            super.visitLdcInsn(value);
                        }
                    }

                    @Override
                    public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrap, Object... args) {
                        if (bootstrap.getOwner().equals("java/lang/invoke/StringConcatFactory")) {
                            throw new IllegalStateException("Use inline Kotlin string concatenation in " + owner);
                        }
                        super.visitInvokeDynamicInsn(name, descriptor, bootstrap, args);
                    }
                };
            }

            @Override
            public void visitEnd() {
                if (!hasInitializer && !constants.isEmpty()) {
                    MethodVisitor initializer = super.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
                    initializer.visitCode();
                    initializeConstants(initializer);
                    initializer.visitInsn(Opcodes.RETURN);
                    initializer.visitMaxs(1, 0);
                    initializer.visitEnd();
                }
                super.visitEnd();
            }

            private void initializeConstants(MethodVisitor method) {
                for (ConstantField field : constants) {
                    emit(method, field.text(), owner + "." + field.name(), signer);
                    method.visitFieldInsn(Opcodes.PUTSTATIC, owner, field.name(), STRING);
                }
            }
        };
    }

    private static void emit(MethodVisitor method, String text, String context, byte[] signer) {
        String encoded = EncodedLiteral.text(text, context, signer);
        if (encoded.length() > 65535) throw new IllegalArgumentException("Encoded literal is too long in " + context);
        method.visitLdcInsn(encoded);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, VAULT, "reveal", "(" + STRING + ")" + STRING, false);
    }

    private record ConstantField(String name, String text) {}
}
