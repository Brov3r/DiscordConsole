package com.brov3r.discordconsole.patches;

import com.avrix.agent.ClassTransformer;
import com.brov3r.discordconsole.ConsoleUtils;
import javassist.CannotCompileException;

/**
 * DebugLog patcher
 */
public class PatchLogger extends ClassTransformer {
    /**
     * Constructor for creating a {@link ClassTransformer} object.
     */
    public PatchLogger() {
        super("zombie.debug.DebugLog");
    }

    /**
     * Method for performing class modification.
     * The implementing method must contain the logic for modifying the target class.
     */
    @Override
    public void modifyClass() {
        getModifierBuilder().modifyMethod("echoToLogFile", (ctClass, ctMethod) -> {
            try {
                ctMethod.insertBefore("{" +
                        ConsoleUtils.class.getName()+".sendLog($1);" +
                        "}");
            } catch (CannotCompileException e) {
                throw new RuntimeException(e);
            }
        });
    }
}