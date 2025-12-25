package tanin.singleinstanceapp;

import java.io.*;
import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.file.Paths;
import java.nio.channels.ServerSocketChannel;
import java.net.StandardProtocolFamily;

public class SingleInstanceApp {
  public interface OnAnotherInstanceActivated {
    void onActivated(String[] args);
  }

  private static boolean isRunning = false;
  private static ServerSocketChannel serverSocket;
  private static Thread loop;

  public static void setUp(
    String[] args,
    String uriScheme,
    OnAnotherInstanceActivated onAnotherInstanceActivated
  ) throws Exception {
    boolean isMac = System.getProperty("os.name")
      .toLowerCase()
      .contains("mac");

    if (isMac) {
      throw new UnsupportedOperationException(
        "MacOS is not supported. For Mac, please use java.awt.Desktop.setOpenURIHandler(..) and Info.plist's LSMultipleInstancesProhibited with either ASWebAuthenticationSession or Info.plist's CFBundleURLSchemes."
      );
    }

    // TODO: Register uriScheme using regedit

    setupSocketOrCommunicate(args, onAnotherInstanceActivated, 2);
  }

  private static void setupSocketOrCommunicate(
    String[] args,
    OnAnotherInstanceActivated onAnotherInstanceActivated,
    int retryCount
  ) throws Exception {

    var localAppDataDir = new File(System.getenv("LOCALAPPDATA"));
    var socketPath = Paths.get(localAppDataDir.getPath(), "app.sock");
    var socketAddress = UnixDomainSocketAddress.of(socketPath);

    serverSocket = ServerSocketChannel.open(StandardProtocolFamily.UNIX);

    try {
      serverSocket.bind(socketAddress);
    } catch (Exception e0) {
      try (SocketChannel client = SocketChannel.open(StandardProtocolFamily.UNIX)) {
        client.connect(socketAddress);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(args);
        oos.flush();
        client.write(ByteBuffer.wrap(baos.toByteArray()));
      } catch (Exception e1) {
        if (retryCount > 0) {
          var _ignored = socketPath.toFile().delete();
          setupSocketOrCommunicate(args, onAnotherInstanceActivated, retryCount - 1);
          return;
        } else {
          // TODO: Make a useful error message about socketPath needing to be deleted.
          throw e1;
        }
      }

      System.exit(0);
    }


    loop = new Thread(() -> {
      isRunning = true;
      while (isRunning) {
        try (SocketChannel clientChannel = serverSocket.accept()) {
          ByteBuffer buffer = ByteBuffer.allocate(1024);
          ByteArrayOutputStream baos = new ByteArrayOutputStream();

          while (clientChannel.read(buffer) > 0) {
            buffer.flip();
            baos.write(buffer.array(), 0, buffer.limit());
            buffer.clear();
          }

          ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()));
          String[] receivedArgs = (String[]) ois.readObject();
          onAnotherInstanceActivated.onActivated(receivedArgs);
        } catch (Exception e) {
          // TODO: Handle raised exception in some way
        }
      }
    });
    loop.setDaemon(true);
    loop.start();
    Runtime.getRuntime().addShutdownHook(new Thread(SingleInstanceApp::shutdown));
  }

  public static void shutdown() {
    isRunning = false;
    try {
      loop.interrupt();
    } catch (Exception ignored) {}
    try {
      serverSocket.close();
    } catch (IOException ignored) {}
  }
}
