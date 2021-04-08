import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Stream;

public class ServerHandler extends Thread {
    private int port;
    ArrayList<File> library;
    private final static String CRLF = "\r\n";
    private static int connectionCount;
    private GUI gui;

    public ServerHandler(int port) {
        this.port = port;
        library = new ArrayList<>();
    }

    @Override
    public void run() {
        ServerSocket socket = null;
        try {
            socket = new ServerSocket(port);
        } catch(IOException e) {
            e.printStackTrace();
        }

        while(true) {
            try {
                Socket connection = socket.accept();
                RequestHandler request = new RequestHandler(connection);
                Thread thread = new Thread(request);
                thread.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void updateLibrary() {
        library.clear();
        try (Stream<Path> paths = Files.walk(Paths.get(new File("./library").getAbsolutePath()))) {
            paths.filter(Files::isDirectory)
                    .forEach(path -> {
                        File dir = new File(path.toString());
                        File[] dirs = dir.listFiles();
                        for (File f : dirs) {
                            if (!f.isDirectory()) {
                                library.add(f);
                            }
                        }                        
                    });
        } catch(IOException e) {
            e.printStackTrace();
        }
    }

    private class RequestHandler implements Runnable {
        private Socket socket;
        private int channel;

        public RequestHandler(Socket socket) {
            this.socket = socket;
            channel = connectionCount++;
        }

        @Override
        public void run() {
            try{
                gui = new GUI();
                gui.loadGUI();
                initChannel();
            }
            catch(Exception e){
                e.printStackTrace();
            }
            finally {
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        private void initChannel() throws Exception {
            String message = "";
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream())); 
            
            gui.setOutputStream(out);
            gui.setTitle("Server: Channel " + channel);
            while(true) {
                message = in.readLine();
                gui.addToScreen(socket.getRemoteSocketAddress() + ": " + message, true);
                if (message.startsWith("!")) {
                    String response = commandHandler(message);
                    if (response.startsWith("file-") && !response.equals("file-")) {
                        System.out.println("ABOUT TO SEND");
                        FileInputStream fis = new FileInputStream(response.replace("file-", ""));
                        sendBytes(fis, out, new File(response.replace("file-", "")));
                    } else {
                        out.writeBytes(response + CRLF);
                    }
                } else {
                    out.writeBytes("" + CRLF);
                }
            }
        }

        private String commandHandler(String command) {
            if (command.trim().equals("!files")) {
                updateLibrary();
                String[] names = new String[library.size()];
                for (int i = 0; i < names.length; i++) {
                    names[i] = library.get(i).getName();
                }
                System.out.println("Files requested...");
                
                return Arrays.toString(names);
            } else if (command.contains("!files")) {
                updateLibrary();
                String[] parts = command.split(" ");
                String[] tempVal = {""};
                library.forEach(file -> {
                    if (file.getName().equals(parts[1])) {
                        tempVal[0] = file.getPath();
                    }
                });
                
                return "file-" + tempVal[0];
            }

            return "";
        }
    }

    private static void sendBytes(FileInputStream fis, DataOutputStream os, File file) throws Exception {
        int bytes = 0;
        
        // send file size
        os.writeLong(file.length());  
        // break file into chunks
        byte[] buffer = new byte[4*1024];
        while ((bytes=fis.read(buffer))!=-1){
            os.write(buffer,0,bytes);
            os.flush();
            // System.out.println("SENDING");
        }
    }
}