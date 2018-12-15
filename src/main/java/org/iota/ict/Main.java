package org.iota.ict;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class Main {

    private static final String DEFAULT_PROPERTY_FILE_PATH = "ict.cfg";
    private static final Map<String, String> ARG_NAME_BY_ARG_ALIAS = new HashMap<>();

    public static final String ARG_CONFIG = "-config";

    static {
        ARG_NAME_BY_ARG_ALIAS.put("-c", ARG_CONFIG);
        ARG_NAME_BY_ARG_ALIAS.put(ARG_CONFIG, ARG_CONFIG);
    }

    public static void main(String[] args) {
        Map<String, String> argMap = mapArgs(args);

        Properties properties = loadOrCreatedProperties(argMap);
        final Ict ict = new Ict(properties);
        System.out.println("Started new Ict on " + ict.getAddress() + ".");

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                System.out.println("Terminating Ict ...");
                ict.terminate();
            }
        });
    }

    private static Properties loadOrCreatedProperties(Map<String, String> argMap) {
        Properties properties = new Properties();
        try {
            properties = tryToLoadOrCreateProperties(argMap);
        } catch (Throwable t) {
            System.err.println("Failed loading properties:");
            t.printStackTrace();
        }
        return properties;
    }

    private static Properties tryToLoadOrCreateProperties(Map<String, String> argMap) {
        if (argMap.containsKey(ARG_CONFIG)) {
            return Properties.fromFile(argMap.get(ARG_CONFIG));
        } else if (new File(DEFAULT_PROPERTY_FILE_PATH).exists()) {
            return Properties.fromFile(DEFAULT_PROPERTY_FILE_PATH);
        } else {
            System.out.println("No property file found, creating new: '" + DEFAULT_PROPERTY_FILE_PATH + "'.");
            Properties properties = new Properties();
            properties.store(DEFAULT_PROPERTY_FILE_PATH);
            return properties;
        }
    }

    private static final Map<String, String> mapArgs(String[] args) {

        Map<String, String> argMap = new HashMap<>();

        for (int i = 0; i < args.length - 1; i++) {
            String arg = args[i].toLowerCase();
            if (arg.charAt(0) == '-') {
                String value = args[i + 1];
                if (argMap.containsKey(arg))
                    System.err.println("multiple values for argument " + arg);
                if (!ARG_NAME_BY_ARG_ALIAS.containsKey(arg))
                    System.err.println("unknown argument '" + arg + "'");
                argMap.put(ARG_NAME_BY_ARG_ALIAS.get(arg), value);
            }
        }

        return argMap;
    }
}
