import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print("$ ");
            String input = scanner.nextLine().trim();

            if (input.equals("exit 0")) {
                break;
            } else if (input.startsWith("echo ")) {
                System.out.println(input.substring(5)); // Print everything after "echo "
            } else {
                System.out.println(input + ": command not found");
            }
        }        
        scanner.close();
    }
}
