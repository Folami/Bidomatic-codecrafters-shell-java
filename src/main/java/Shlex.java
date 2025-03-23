
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