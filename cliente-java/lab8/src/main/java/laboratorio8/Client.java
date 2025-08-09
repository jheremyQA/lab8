package laboratorio8;

import javax.swing.*;
import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;

public class Client {

    private static final String SERVER_HOST = "161.132.51.124";
    private static final int SERVER_PORT = 5000;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Subir Archivo al Servidor");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(500, 150);
            frame.setLocationRelativeTo(null);
            
            JPanel panel = new JPanel();
            JTextField pathField = new JTextField(25);
            JButton browseButton = new JButton("Seleccionar Archivo");
            JButton uploadButton = new JButton("Subir Archivo");

            final File[] selectedFile = {null};

            browseButton.addActionListener(e -> {
                JFileChooser fileChooser = new JFileChooser();
                if (fileChooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
                    selectedFile[0] = fileChooser.getSelectedFile();
                    pathField.setText("/" + selectedFile[0].getName());
                }
            });

            uploadButton.addActionListener(e -> {
                if (selectedFile[0] == null || pathField.getText().trim().isEmpty()) {
                    JOptionPane.showMessageDialog(frame, "Selecciona un archivo y una ruta válida.");
                    return;
                }
                uploadFile(selectedFile[0], pathField.getText());
            });

            panel.add(browseButton);
            panel.add(pathField);
            panel.add(uploadButton);
            frame.add(panel);
            frame.setVisible(true);
        });
    }

    private static void uploadFile(File file, String remotePath) {
        try (Socket socket = new Socket(SERVER_HOST, SERVER_PORT); OutputStream out = socket.getOutputStream(); InputStream in = socket.getInputStream(); BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"))) {

            // Paso 1: Enviar ruta
            out.write((remotePath + "\n").getBytes("UTF-8"));

            // Paso 2: Esperar respuesta
            String response = reader.readLine();
            if ("EXISTS".equals(response)) {
                int choice = JOptionPane.showOptionDialog(null,
                        "El archivo ya existe. ¿Deseas sobrescribirlo o renombrarlo?",
                        "Conflicto de archivo",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE,
                        null,
                        new Object[]{"Sobrescribir", "Renombrar"},
                        "Sobrescribir");

                if (choice == 0) {
                    out.write("OVERWRITE\n".getBytes("UTF-8"));
                } else {
                    String newName = JOptionPane.showInputDialog("Nuevo nombre para el archivo:");
                    if (newName == null || newName.trim().isEmpty()) {
                        out.write("CANCEL\n".getBytes("UTF-8"));
                        return;
                    } else {
                        // Verificar si tiene extensión, si no, agregar la del archivo original
                        String originalName = file.getName();
                        int dotIndex = originalName.lastIndexOf(".");
                        if (dotIndex != -1 && !newName.contains(".")) {
                            String extension = originalName.substring(dotIndex);
                            newName += extension;
                        }
                        out.write(("RENAME:" + newName + "\n").getBytes("UTF-8"));
                    }
                }

                // Esperar confirmación del servidor para continuar
                String confirm = reader.readLine();
                if (!"OK".equals(confirm)) {
                    JOptionPane.showMessageDialog(null, confirm);
                    return;
                }
            } else if (!"OK".equals(response)) {
                JOptionPane.showMessageDialog(null, "Respuesta inesperada: " + response);
                return;
            }

            // Paso 3: Enviar tamaño del archivo
            out.write(ByteBuffer.allocate(8).putLong(file.length()).array());

            // Paso 4: Enviar contenido del archivo
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[4096];
                int read;
                while ((read = fis.read(buffer)) > 0) {
                    out.write(buffer, 0, read);
                }
            }

            // Paso 5: Leer confirmación final
            String finalResponse = reader.readLine();
            JOptionPane.showMessageDialog(null, finalResponse);

        } catch (IOException ex) {
            JOptionPane.showMessageDialog(null, "Error al subir archivo: " + ex.getMessage());
        }
    }
}
