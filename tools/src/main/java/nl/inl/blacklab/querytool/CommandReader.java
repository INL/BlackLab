package nl.inl.blacklab.querytool;

import java.io.BufferedReader;
import java.io.IOException;
import java.lang.reflect.Method;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;

class CommandReader {

    private final BufferedReader in;

    private final Output output;

    /**
     * If JLine is available, this holds the ConsoleReader object
     */
    private Object jlineConsoleReader;

    /**
     * If JLine is available, this holds the readLine() method
     */
    private Method jlineReadLineMethod;

    /**
     * Did we check if JLine is available?
     */
    private boolean jlineChecked = false;

    public CommandReader(BufferedReader in, Output output) {
        this.in = in;
        this.output = output;
    }

    public String readCommand(String prompt) throws IOException {
        if (!output.isBatchMode() && jlineConsoleReader == null && !jlineChecked) {
            jlineChecked = true;
            try {
                Class<?> c = Class.forName("jline.ConsoleReader");
                jlineConsoleReader = c.getConstructor().newInstance();

                // Disable bell
                c.getMethod("setBellEnabled", boolean.class).invoke(jlineConsoleReader, false);

                // Fetch and store the readLine method
                jlineReadLineMethod = c.getMethod("readLine", String.class);

                output.line("Command line editing enabled.");
            } catch (ClassNotFoundException e) {
                // Can't init JLine; too bad, fall back to stdin
                output.line("Command line editing not available; to enable, place jline jar in classpath.");
            } catch (ReflectiveOperationException e) {
                throw new BlackLabRuntimeException("Could not init JLine console reader", e);
            }
        }

        if (jlineConsoleReader != null) {
            try {
                return (String) jlineReadLineMethod.invoke(jlineConsoleReader, prompt);
            } catch (ReflectiveOperationException e) {
                throw new BlackLabRuntimeException("Could not invoke JLine ConsoleReader.readLine()", e);
            }
        }

        if (!output.isBatchMode())
            output.noNewLine(prompt);
        output.flush();
        return in.readLine();
    }
}
