package com.straube.jones.dataprovider.userprefs;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import com.straube.jones.model.User;

public class UserPrefsRepo {
    private static final String USER_PREFS_ROOT = "/opt/tomcat/data/userprefs";

    static {
        try {
            Files.createDirectories(new File(USER_PREFS_ROOT).toPath());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private UserPrefsRepo() {
    }

    public static String getPrefs(User currentUser, String topic)
            throws IOException {
        String[] subDirs = topic.split("#");
        String filename = subDirs[subDirs.length - 1];
        StringBuilder pathBuilder = new StringBuilder();
        pathBuilder.append(USER_PREFS_ROOT).append("/").append(currentUser.getId());
        for (int i = 0; i < subDirs.length - 1; i++) {
            pathBuilder.append("/").append(subDirs[i]);
        }
        File f = new File(pathBuilder.toString(), filename + ".json");
        if (!f.exists()) {
            return "";
        }
        return new String(Files.readAllBytes(f.toPath()));
    }

    public static boolean savePrefs(User currentUser, String topic, String stocks) {
        String[] subDirs = topic.split("#");
        String filename = subDirs[subDirs.length - 1];

        try {
            if (currentUser == null) {
                File[] fUsers = Files.list(new File(USER_PREFS_ROOT).toPath()).toArray(File[]::new);
                if (fUsers.length == 0) {
                    return true;
                }
                for (File fUser : fUsers) {
                    fUser.mkdirs();
                    File f = new File(fUser, filename + ".json");
                    Files.write(f.toPath(), stocks.getBytes());
                }
                return true;
            }
            StringBuilder pathBuilder = new StringBuilder();
            pathBuilder.append(USER_PREFS_ROOT).append("/").append(currentUser.getId());
            for (int i = 0; i < subDirs.length - 1; i++) {
                pathBuilder.append("/").append(subDirs[i]);
            }
            File dir = new File(USER_PREFS_ROOT, String.valueOf(currentUser.getId()) + "/" + pathBuilder.toString());
            if (!dir.exists()) {
                dir.mkdir();
            }
            File folder = new File(pathBuilder.toString());
            if (!folder.exists()) {
                folder.mkdirs();
            }
            File f = new File(folder, filename + ".json");
            Files.write(f.toPath(), stocks.getBytes());
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }
}
