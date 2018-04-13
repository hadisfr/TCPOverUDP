public class ConsoleLog {
    private static final Boolean verbose = true;
    private static final Boolean veryVerbose = false;
    private static final Boolean fullColour = true;

    private static enum Colour {
        NC(39), BLACK(30), RED(31), GREEN(32), YELLOW(33), BLUE(94), MAGENTA(35), CYAN(36);

        private final int code;

        private Colour(int code) {
            this.code = code;
        }

        public String toString() {
            return "\033[" + this.code + "m";
        }
    }

    private static String beautify(String str, Colour colour) {
        return fullColour ? colour + str + Colour.NC : str;
    }

    public static void log(String str) {
        if (verbose)
            System.err.println(str);
    }

    public static void handshakingLog(String str) {
        log(beautify(str, Colour.MAGENTA));
    }

    public static void connectionLog(String str) {
        log(beautify(str, Colour.CYAN));
    }

    public static void fileLog(String str) {
        if (veryVerbose)
            log(beautify(str, Colour.BLUE));
    }
}
