package com.straube.jones.dataprovider.userprefs;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import com.straube.jones.model.User;

public class UserPrefsRepo {
    private static final String USER_PREFS_ROOT = "/opt/tomcat/data/userprefs/";

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
        File f = new File(USER_PREFS_ROOT, String.valueOf(currentUser.getId()) + "/" + topic + ".json");
        if (!f.exists()) {
            return "";
        }
        return new String(Files.readAllBytes(f.toPath()));
    }

    public static boolean savePrefs(User currentUser, String topic, String stocks) {
        try {
            if (currentUser == null) {
                File[] fUsers = Files.list(new File(USER_PREFS_ROOT).toPath()).toArray(File[]::new);
                if (fUsers.length == 0) {
                    return true;
                }
                for (File fUser : fUsers) {
                    fUser.mkdirs();
                    File f = new File(fUser, topic + ".json");
                    Files.write(f.toPath(), stocks.getBytes());
                }
                return true;
            }
            File f = new File(USER_PREFS_ROOT, String.valueOf(currentUser.getId()) + "/" + topic + ".json");
            f.getParentFile().mkdirs();
            Files.write(f.toPath(), stocks.getBytes());
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }
}
