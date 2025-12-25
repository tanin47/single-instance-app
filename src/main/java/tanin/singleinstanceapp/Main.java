package tanin.singleinstanceapp;

import java.util.concurrent.atomic.AtomicBoolean;

public class Main {
  public static void main(String[] args) throws Exception {
    SingleInstanceApp.setUp(
      args,
      "fingertipai",
      (anotherInstanceArgs) -> {
        System.out.println(String.join(" ", anotherInstanceArgs));
      }
    );

    AtomicBoolean isRunning = new AtomicBoolean(true);
    Runtime.getRuntime().addShutdownHook(new Thread(() -> { isRunning.set(false); }));
    while (isRunning.get()) {
      Thread.sleep(10000);
      System.out.println(".");
    }
    System.out.println("Exiting...");
  }
}
