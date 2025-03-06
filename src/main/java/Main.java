import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.io.Console;
import java.util.stream.Collectors;


public class Main {
    private static final String shellHome = System.getProperty("user.dir");
    private static final List<String> shBuiltins = List.of("echo", "exit", "type", "pwd", "cd");

    public static class AutoCompleter { // Moved outside Main class
        private static final List<String> builtins = List.of("echo", "exit", "pwd", "cd", "type");

        public static String complete(String partial) {
            List<String> matches = builtins.stream()
                    .filter(cmd -> cmd.startsWith(partial))
                    .collect(Collectors.toList());

            if (!matches.isEmpty()) {
                return matches.get(0);
            } else {
                return partial;
            }
        }
    }
    
    public static class Shlex {
        public static List<String> split(String s, boolean comments, boolean posix) throws IOException {
            if (s == null) {
                throw new IllegalArgumentException("s argument must not be null");
            }
            Shlex lex = new Shlex(s, null, posix, null);
            lex.whitespaceSplit = true;
            if (!comments) {
                lex.commenters = "";
            }
            return lex.split();
        }

        private StringReader instream;
        private String infile;
        private boolean posix;
        private String eof;
        private String commenters = "#";
        private String wordchars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_";
        private String whitespace = " \t\r\n";
        private boolean whitespaceSplit = false;
        private String quotes = "'\"";
        private String escape = "\\";
        private String escapedquotes = "\"";
        private String state = " ";
        private Deque<String> pushback = new LinkedList<>();
        private int lineno = 1;
        private int debug = 0;
        private String token = "";
        private Deque<Object[]> filestack = new LinkedList<>();
        private String source = null;
        private String punctuationChars = "";
        private Deque<Character> pushbackChars = new LinkedList<>();
        private boolean quoted = false;
        private String escapedstate = " ";

        public Shlex(String instream, String infile, boolean posix, String punctuationChars) {
            this.instream = new StringReader(instream);
            this.infile = infile;
            this.posix = posix;
            this.punctuationChars = punctuationChars == null ? "" : punctuationChars;
            if (posix) {
                this.eof = null;
            } else {
                this.eof = "";
            }
            if (posix) {
                this.wordchars += "ßàáâãäåæçèéêëìíîïðñòóôõöøùúûüýþÿÀÁÂÃÄÅÆÇÈÉÊËÌÍÎÏÐÑÒÓÔÕÖØÙÚÛÜÝÞ";
            }
            if (!this.punctuationChars.isEmpty()) {
                this.wordchars += "~-./*?=";
                for (char c : this.punctuationChars.toCharArray()) {
                    this.wordchars = this.wordchars.replace(String.valueOf(c), "");
                }
            }
        }

        public List<String> split() throws IOException {
            List<String> tokens = new ArrayList<>();
            while (true) {
                String token = get_token();
                if (token == null) {
                    break;
                }
                tokens.add(token);
            }
            return tokens;
        }

        private void push_token(String tok) {
            if (debug >= 1) {
                System.out.println("shlex: pushing token " + tok);
            }
            pushback.addFirst(tok);
        }

        private String get_token() throws IOException {
            if (!pushback.isEmpty()) {
                String tok = pushback.removeFirst();
                if (debug >= 1) {
                    System.out.println("shlex: popping token " + tok);
                }
                return tok;
            }
            String raw = read_token();
            while (raw != null && raw.equals(eof)) {
                if (filestack.isEmpty()) {
                    return eof;
                } else {
                    pop_source();
                    raw = get_token();
                }
            }
            if (debug >= 1) {
                if (raw != null && !raw.equals(eof)) {
                    System.out.println("shlex: token=" + raw);
                } else {
                    System.out.println("shlex: token=EOF");
                }
            }
            return raw;
        }

        private String read_token() throws IOException {
            while (true) {
                char nextchar = getNextChar();
                if (nextchar == '\n') {
                    lineno++;
                }
                if (debug >= 3) {
                    System.out.println("shlex: in state " + state + " I see character: " + nextchar);
                }
                if (state == null) {
                    token = "";
                    break;
                } else if (state.equals(" ")) {
                    handleWhitespaceState(nextchar, quoted);
                } else if (quotes.indexOf(state) != -1) {
                    handleQuotedState(nextchar, state);
                } else if (escape.indexOf(state) != -1) {
                    handleEscapeState(nextchar, escapedstate);
                } else if (state.equals("a") || state.equals("c")) {
                    handleAlphaOrPunctuationState(nextchar, quoted);
                }
            }
            return handleTokenCompletion(quoted);
        }

        private char getNextChar() throws IOException {
            if (!punctuationChars.isEmpty() && !pushbackChars.isEmpty()) {
                return pushbackChars.removeFirst();
            } else {
                int readChar = instream.read();
                return readChar == -1 ? '\0' : (char) readChar;
            }
        }

        private void handleWhitespaceState(char nextchar, boolean quoted) throws IOException {
            if (nextchar == '\0') {
                state = null;
            } else if (whitespace.indexOf(nextchar) != -1) {
                handleWhitespace(quoted);
            } else if (commenters.indexOf(nextchar) != -1) {
                handleComment();
            } else if (posix && escape.indexOf(nextchar) != -1) {
                handleEscapeStart(nextchar);
            } else if (wordchars.indexOf(nextchar) != -1) {
                handleWordChar(nextchar);
            } else if (punctuationChars.indexOf(nextchar) != -1) {
                handlePunctuationChar(nextchar);
            } else if (quotes.indexOf(nextchar) != -1) {
                handleQuote(nextchar);
            } else if (whitespaceSplit) {
                handleWhitespaceSplit(nextchar);
            } else {
                handleOtherChar(nextchar, quoted);
            }
        }

        private void handleQuotedState(char nextchar, String state) throws IOException {
            quoted = true;
            if (nextchar == '\0') {
                throw new IllegalArgumentException("No closing quotation");
            }
            if (String.valueOf(nextchar).equals(state)) {
                handleClosingQuote(nextchar, state);
            } else if (posix && escape.indexOf(nextchar) != -1 && escapedquotes.indexOf(state) != -1) {
                handleEscapeInQuote(nextchar, state);
            } else {
                token += nextchar;
            }
        }


        private void handleEscapeState(char nextchar, String escapedstate) throws IOException {
            if (nextchar == '\0') {
                throw new IllegalArgumentException("No escaped character");
            }
            handleEscapedChar(nextchar, escapedstate);
            state = escapedstate;
        }


        private void handleAlphaOrPunctuationState(char nextchar, boolean quoted) throws IOException {
            if (nextchar == '\0') {
                state = null;
            } else if (whitespace.indexOf(nextchar) != -1) {
                handleWhitespace(quoted);
            } else if (commenters.indexOf(nextchar) != -1) {
                handleCommentWithPosixCheck(quoted);
            } else if (state.equals("c")) {
                handlePunctuationContinuation(nextchar);
            } else if (posix && quotes.indexOf(nextchar) != -1) {
                handleQuoteInAlphaState(nextchar);
            } else if (posix && escape.indexOf(nextchar) != -1) {
                handleEscapeStart(nextchar);
            } else if (wordchars.indexOf(nextchar) != -1 || quotes.indexOf(nextchar) != -1
                    || (whitespaceSplit && punctuationChars.indexOf(nextchar) == -1)) {
                token += nextchar;
            } else {
                handleOtherCharInAlphaState(nextchar);
            }
        }

        private String handleTokenCompletion(boolean quoted) {
            String result = token;
            token = "";
            if (posix && !quoted && result.isEmpty()) {
                result = null;
            }
            if (debug > 1) {
                System.out.println("shlex: raw token=" + result);
            }
            return result;
        }

        // Helper functions (implementations below)
        private void handleWhitespace(boolean quoted) {
            if (debug >= 2) {
                System.out.println("shlex: I see whitespace in whitespace state");
            }
            if (!token.isEmpty() || (posix && quoted)) {
                state = " ";
            }
        }

        private void handleComment() throws IOException {
            instream.read();
            lineno++;
        }

        private void handleEscapeStart(char nextchar) {
            escapedstate = "a";
            state = String.valueOf(nextchar);
        }

        private void handleWordChar(char nextchar) {
            token = String.valueOf(nextchar);
            state = "a";
        }

        private void handlePunctuationChar(char nextchar) {
            token = String.valueOf(nextchar);
            state = "c";
        }

        private void handleQuote(char nextchar) {
            if (!posix) {
                token = String.valueOf(nextchar);
            }
            state = String.valueOf(nextchar);
        }

        private void handleWhitespaceSplit(char nextchar) {
            token = String.valueOf(nextchar);
            state = "a";
        }

        private void handleOtherChar(char nextchar, boolean quoted) {
            token = String.valueOf(nextchar);
            if (!token.isEmpty() || (posix && quoted)) {
                state = " ";
            }
        }

        private void handleClosingQuote(char nextchar, String state) {
            if (!posix) {
                token += nextchar;
                state = " ";
            } else {
                state = "a";
            }
        }

        private void handleEscapeInQuote(char nextchar, String state) {
            escapedstate = state;
            state = String.valueOf(nextchar);
        }

        private void handleEscapedChar(char nextchar, String escapedstate) {
            if (quotes.indexOf(escapedstate) != -1 && nextchar != state.charAt(0) && nextchar != escapedstate.charAt(0)) {
                token += state;
            }
            token += nextchar;
        }

        private void handleCommentWithPosixCheck(boolean quoted) throws IOException {
            instream.read();
            lineno++;
            if (posix) {
                handleWhitespace(quoted);
            }
        }

        private void handlePunctuationContinuation(char nextchar) {
            if (punctuationChars.indexOf(nextchar) != -1) {
                token += nextchar;
            } else {
                if (whitespace.indexOf(nextchar) == -1) {
                    pushbackChars.addFirst(nextchar);
                }
                state = " ";
            }
        }

        private void handleQuoteInAlphaState(char nextchar) {
            state = String.valueOf(nextchar);
        }

        private void handleOtherCharInAlphaState(char nextchar) {
            if (!punctuationChars.isEmpty()) {
                pushbackChars.addFirst(nextchar);
            } else {
                pushback.addFirst(String.valueOf(nextchar));
            }
            state = " ";
        }

        private void pop_source() throws IOException {
            instream.close();
            Object[] sourceInfo = filestack.removeFirst();
            infile = (String) sourceInfo[0];
            instream = new StringReader((String) sourceInfo[1]);
            lineno = (int) sourceInfo[2];
            if (debug != 0) {
                System.out.println("shlex: popping to " + instream + ", line " + lineno);
            }
            state = " ";
        }
    }
    
    
    public static void main(String[] args) {
        while (true) {
            String commandLine = inputPrompt();
            if (commandLine == null || commandLine.isEmpty()) {
                continue;
            }
            try {
                List<String> tokens = Shlex.split(commandLine, true, true);
                if (tokens == null || tokens.isEmpty()) {
                    continue;
                }

                String command = tokens.get(0);
                List<String> commandArgs = tokens.subList(1, tokens.size());
                executeCommand(command, commandArgs);

            } catch (IllegalArgumentException e) {
                System.out.println("Error parsing command: " + e.getMessage());
            } catch (IOException e) {
                System.out.println("An error occurred: " + e.getMessage());
            }
        }
    }

    private static String inputPrompt() {
        Console console = System.console();
        if (console == null) {
            // Fallback for non-interactive terminals
            System.out.print("$ ");
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            try {
                return reader.readLine();
            } catch (IOException e) {
                return null;
            }
        }

        System.out.print("$ ");
        StringBuilder inputBuffer = new StringBuilder();
        try {
            while (true) {
                int key = System.in.read();
                if (key == '\n') {
                    System.out.println();
                    return inputBuffer.toString();
                } else if (key == '\t') {
                    // Get the completed command
                    String completed = AutoCompleter.complete(inputBuffer.toString());
                    
                    // Clear the current line and reprint with completed command
                    System.out.print("\r");          // Return to beginning of line
                    System.out.print("$ ");          // Print prompt
                    System.out.print(completed);     // Print completed command
                    
                    // Update input buffer with completed command
                    inputBuffer.setLength(0);
                    inputBuffer.append(completed);
                } else if (key == 127 || key == 8) { // Handle backspace
                    if (inputBuffer.length() > 0) {
                        inputBuffer.setLength(inputBuffer.length() - 1);
                        System.out.print("\b \b");
                    }
                } else {
                    inputBuffer.append((char) key);
                    System.out.print((char) key);
                }
            }
        } catch (IOException e) {
            return null;
        }
    }


    private static void executeCommand(String command, List<String> args) throws IOException {
        switch (command) {
            case "exit":
                exitShell();
                break;
            case "echo":
                executeEcho(args);
                break;
            case "type":
                executeType(args);
                break;
            case "pwd":
                executePwd();
                break;
            case "cd":
                executeCd(args);
                break;
            default:
                runExternalCommand(command, args);
                break;
        }
    }

    private static void exitShell() {
        System.exit(0);
    }

    private static void executeEcho(List<String> args) throws IOException {
        String outputFile = null;       // For stdout redirection (>)
        String appendOutputFile = null; // For stdout append redirection (>> or 1>>)
        String errorFile = null;        // For stderr redirection (2>)
        String appendErrorFile = null;  // For stderr append redirection (2>>)
        List<String> echoArgs = new ArrayList<>();

        // Parse arguments for redirection
        for (int i = 0; i < args.size(); i++) {
            if (args.get(i).equals(">") || args.get(i).equals("1>")) {
                if (i + 1 < args.size()) {
                    outputFile = args.get(i + 1);
                    i++; // Skip file name
                } else {
                    System.err.println("Syntax error: no file specified for redirection");
                    return;
                }
            } else if (args.get(i).equals(">>") || args.get(i).equals("1>>")) {
                if (i + 1 < args.size()) {
                    appendOutputFile = args.get(i + 1);
                    i++; // Skip file name
                } else {
                    System.err.println("Syntax error: no file specified for append redirection");
                    return;
                }
            } else if (args.get(i).equals("2>")) {
                if (i + 1 < args.size()) {
                    errorFile = args.get(i + 1);
                    i++; // Skip file name
                } else {
                    System.err.println("Syntax error: no file specified for error redirection");
                    return;
                }
            } else if (args.get(i).equals("2>>")) {
                if (i + 1 < args.size()) {
                    appendErrorFile = args.get(i + 1);
                    i++; // Skip file name
                } else {
                    System.err.println("Syntax error: no file specified for error append redirection");
                    return;
                }
            } else {
                echoArgs.add(args.get(i));
            }
        }

        String output = String.join(" ", echoArgs);

        // Handle stdout redirection (overwrite)
        if (outputFile != null) {
            File outputFileObj = new File(outputFile);
            if (!outputFileObj.getParentFile().exists()) {
                if (!outputFileObj.getParentFile().mkdirs()) {
                    System.err.println("Error: unable to create directory for output redirection file");
                    return;
                }
            }
            try (java.io.PrintWriter out = new java.io.PrintWriter(new java.io.FileWriter(outputFileObj))) {
                out.println(output); // Write output with newline for stdout
            }
        }
        // Handle stdout redirection (append)
        else if (appendOutputFile != null) {
            File appendOutputFileObj = new File(appendOutputFile);
            if (!appendOutputFileObj.getParentFile().exists()) {
                if (!appendOutputFileObj.getParentFile().mkdirs()) {
                    System.err.println("Error: unable to create directory for append redirection file");
                    return;
                }
            }
            try (java.io.PrintWriter out = new java.io.PrintWriter(new java.io.FileWriter(appendOutputFileObj, true))) {
                out.println(output); // Append output with newline
            }
        } else {
            // If no stdout redirection, print to console
            System.out.println(output);
        }

        // Handle stderr redirection (overwrite)
        if (errorFile != null) {
            File errorFileObj = new File(errorFile);
            if (!errorFileObj.getParentFile().exists()) {
                if (!errorFileObj.getParentFile().mkdirs()) {
                    System.err.println("Error: unable to create directory for error redirection file");
                    return;
                }
            }
            try (java.io.PrintWriter errOut = new java.io.PrintWriter(new java.io.FileWriter(errorFileObj))) {
                errOut.print(""); // No stderr for echo by default
            }
        }
        // Handle stderr redirection (append)
        else if (appendErrorFile != null) {
            File appendErrorFileObj = new File(appendErrorFile);
            if (!appendErrorFileObj.getParentFile().exists()) {
                if (!appendErrorFileObj.getParentFile().mkdirs()) {
                    System.err.println("Error: unable to create directory for error append redirection file");
                    return;
                }
            }
            try (java.io.PrintWriter errOut = new java.io.PrintWriter(new java.io.FileWriter(appendErrorFileObj, true))) {
                errOut.print(""); // No stderr for echo by default, append nothing
            }
        }
    }

    private static void executeType(List<String> args) {
        if (args.isEmpty()) {
            System.out.println("type: missing operand");
            return;
        }
        String targetCommand = args.get(0);
        if (shBuiltins.contains(targetCommand)) {
            System.out.println(targetCommand + " is a shell builtin");
            } else {
            String executable = findExecutable(targetCommand);
            if (executable != null) {
                System.out.println(targetCommand + " is " + executable);
            } else {
                System.out.println(targetCommand + ": not found");
            }
        }
    }

    private static void executePwd() {
        System.out.println(System.getProperty("user.dir"));
    }

    private static void executeCd(List<String> args) {
        if (args.isEmpty()) {
            System.out.println("cd: missing operand");
            return;
        }
        String newDir = args.get(0);
        if (newDir.startsWith("~")) {
            newDir = System.getenv("HOME") + newDir.substring(1);
        }
        Path currentPath = Paths.get(System.getProperty("user.dir"));
        Path newPath = currentPath.resolve(newDir).normalize();
        try {
            if (Files.exists(newPath) && Files.isDirectory(newPath)) {
                System.setProperty("user.dir", newPath.toString());
            } else {
                System.err.println("cd: " + newDir + ": No such file or directory");
            }
        } catch (Exception e) {
            System.err.println("cd: " + newDir + ": " + e.getMessage());
        }
    }


    private static String findExecutable(String command) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            String[] paths = pathEnv.split(File.pathSeparator);
            for (String dir : paths) {
                Path filePath = Paths.get(dir, command);
                if (Files.exists(filePath) && Files.isExecutable(filePath)) {
                    return filePath.toString();
                }
            }
        }
        // Check in the current directory
        Path currentDir = Paths.get(System.getProperty("user.dir"));
        Path filePath = currentDir.resolve(command);
        if (Files.exists(filePath) && Files.isExecutable(filePath)) {
            return filePath.toString();
        }
        return null;
    }


    private static void runExternalCommand(String command, List<String> args) throws IOException {
        if (findExecutable(command) == null) {
            System.err.println(command + ": command not found");
            return;
        }
        List<String> commandWithArgs = new ArrayList<>();
        commandWithArgs.add(command);
        
        String outputFile = null;       // For stdout overwrite (>)
        String appendOutputFile = null; // For stdout append (>> or 1>>)
        String errorFile = null;        // For stderr overwrite (2>)
        String appendErrorFile = null;  // For stderr append (2>>)
        for (int i = 0; i < args.size(); i++) {
            if (args.get(i).equals(">") || args.get(i).equals("1>")) {
                if (i + 1 < args.size()) {
                    outputFile = args.get(i + 1);
                    i++; // Skip the next argument (file name)
                } else {
                    System.err.println("Syntax error: no file specified for redirection");
                    return;
                }
            } else if (args.get(i).equals(">>") || args.get(i).equals("1>>")) {
                if (i + 1 < args.size()) {
                    appendOutputFile = args.get(i + 1);
                    i++; // Skip the next argument (file name)
                } else {
                    System.err.println("Syntax error: no file specified for append redirection");
                    return;
                }
            } else if (args.get(i).equals("2>")) {
                if (i + 1 < args.size()) {
                    errorFile = args.get(i + 1);
                    i++; // Skip the next argument (file name)
                } else {
                    System.err.println("Syntax error: no file specified for error redirection");
                    return;
                }
            } else if (args.get(i).equals("2>>")) {
                if (i + 1 < args.size()) {
                    appendErrorFile = args.get(i + 1);
                    i++; // Skip the next argument (file name)
                } else {
                    System.err.println("Syntax error: no file specified for error append redirection");
                    return;
                }
            } else {
                commandWithArgs.add(args.get(i));
            }
        }

        ProcessBuilder processBuilder = new ProcessBuilder(commandWithArgs);
        if (outputFile != null) {
            File outputFileObj = new File(outputFile);
            if (!outputFileObj.getParentFile().exists()) {
                if (!outputFileObj.getParentFile().mkdirs()) {
                    System.err.println("Error: unable to create directory for output redirection file");
                    return;
                }
            }
            processBuilder.redirectOutput(ProcessBuilder.Redirect.to(outputFileObj));
            processBuilder.redirectError(ProcessBuilder.Redirect.PIPE); // Capture error stream
        } else if (appendOutputFile != null) {
            File appendOutputFileObj = new File(appendOutputFile);
            if (!appendOutputFileObj.getParentFile().exists()) {
                if (!appendOutputFileObj.getParentFile().mkdirs()) {
                    System.err.println("Error: unable to create directory for append redirection file");
                    return;
                }
            }
            processBuilder.redirectOutput(ProcessBuilder.Redirect.appendTo(appendOutputFileObj));
            processBuilder.redirectError(ProcessBuilder.Redirect.PIPE); // Capture error stream
        } else if (errorFile != null) {
            File errorFileObj = new File(errorFile);
            if (!errorFileObj.getParentFile().exists()) {
                if (!errorFileObj.getParentFile().mkdirs()) {
                    System.err.println("Error: unable to create directory for error redirection file");
                    return;
                }
            }
            processBuilder.redirectError(ProcessBuilder.Redirect.to(errorFileObj));
        } else if (appendErrorFile != null) {
            File appendErrorFileObj = new File(appendErrorFile);
            if (!appendErrorFileObj.getParentFile().exists()) {
                if (!appendErrorFileObj.getParentFile().mkdirs()) {
                    System.err.println("Error: unable to create directory for error append redirection file");
                    return;
                }
            }
            processBuilder.redirectError(ProcessBuilder.Redirect.appendTo(appendErrorFileObj));
        } else {
            processBuilder.redirectErrorStream(true);
        }

        try {
            Process process = processBuilder.start();
            if (outputFile == null && appendOutputFile == null && errorFile == null && appendErrorFile == null) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println(line);
                    }
                }
            } else if (outputFile != null || appendOutputFile != null) {
                if (errorFile == null && appendErrorFile == null) {
                    try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                        String errorLine;
                        while ((errorLine = errorReader.readLine()) != null) {
                            System.err.println(errorLine); // Print error messages
                        }
                    }
                }
            } else if (errorFile != null || appendErrorFile != null) {
                try (BufferedReader outputReader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String outputLine;
                    while ((outputLine = outputReader.readLine()) != null) {
                        System.out.println(outputLine); // Print output
                    }
                }
            }
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                if (outputFile != null || appendOutputFile != null || errorFile != null || appendErrorFile != null) {
                    // Do not print generic error message if output or error is redirected
                } else {
                    System.err.println(command + ": command failed with exit code " + exitCode);
                }
            }
        } catch (IOException e) {
            if (e.getMessage().contains("Cannot run program")) {
                System.err.println(command + ": command not found");
            } else {
                System.err.println(command + ": " + e.getMessage());
            }
        } catch (InterruptedException e) {
            System.err.println(command + ": process interrupted");
            Thread.currentThread().interrupt();
        }
    }
}


