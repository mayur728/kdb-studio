package studio.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

public class ClassLoaderUtil {
    protected static String classToPath(String name) {
        Properties properties = System.getProperties();
        String fileSeparator = properties.getProperty("file.separator");
        char fsc = fileSeparator.charAt(0);
        String path = name.replace('.', fsc);
        path += ".class";
        return path;
    }

    protected static byte[] readFile(String filename) throws IOException {
        File file = new File(filename);
        long len = file.length();
        byte[] data = new byte[(int) len];
        FileInputStream fin = new FileInputStream(file);
        int r = fin.read(data);
        if (r != len) {
            throw new IOException("Only read " + r + " of " + len + " for " + file);
        }
        fin.close();
        return data;
    }

    protected static byte[] getClassBytes(String name) throws IOException {
        String path = classToPath(name);
        return readFile(path);
    }

    protected static void copyFile(OutputStream out, InputStream in)
        throws IOException {
        byte[] buffer = new byte[4096];

        while (true) {
            int r = in.read(buffer);
            if (r <= 0) {
                break;
            }
            out.write(buffer, 0, r);
        }
    }

    protected static void copyFile(OutputStream out, String infile)
        throws IOException {
        FileInputStream fin = new FileInputStream(infile);
        copyFile(out, fin);
        fin.close();
    }
}




