package edu.itc.se.lab01.task5;

public class Main {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("--- OrderedBank (Fix A) ---");
        OrderedBank.main(args);

        System.out.println("\n--- TryLockBank (Fix B) ---");
        TryLockBank.main(args);

        System.out.println("\n--- GracefulShutdown ---");
        GracefulShutdown.main(args);
    }
}
