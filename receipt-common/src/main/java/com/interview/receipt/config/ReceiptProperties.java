package com.interview.receipt.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "receipt")
public class ReceiptProperties {
    private final Storage storage = new Storage();
    private final Redis redis = new Redis();

    public Storage getStorage() {
        return storage;
    }

    public Redis getRedis() {
        return redis;
    }

    public static class Storage {
        private String connectionString = "";

        public String getConnectionString() {
            return connectionString;
        }

        public void setConnectionString(String connectionString) {
            this.connectionString = connectionString;
        }
    }

    public static class Redis {
        private boolean enabled;
        private String host = "";
        private int port = 10000;
        private String password = "";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }
}
