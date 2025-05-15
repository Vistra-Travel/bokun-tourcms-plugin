package io.bokun.inventory.plugin.tourcms;

public final class Configuration {
    public static final String TOURCMS_ACCOUNT_ID = "TOURCMS_ACCOUNT_ID";
    public static final String TOURCMS_PRIVATE_KEY = "TOURCMS_PRIVATE_KEY";
    public static final String TOURCMS_CHANNEL_ID = "TOURCMS_CHANNEL_ID";
    public static final String TOURCMS_FILTER_IDS = "TOURCMS_FILTER_IDS";
    public static final String SMTP_SERVER = "SMTP_SERVER";
    public static final String SMTP_USERNAME = "SMTP_USERNAME";
    public static final String SMTP_PASSWORD = "SMTP_PASSWORD";

    public String marketplaceId;
    public String channelId;
    public String apiKey;
    public String filterIds;

    public String smtpServer;
    public String smtpUsername;
    public String smtpPassword;

    private static void setParameterValue(String parameterName, String parameterValue, Configuration configuration) {
        switch (parameterName) {
            case TOURCMS_ACCOUNT_ID: configuration.marketplaceId = parameterValue; break;
            case TOURCMS_CHANNEL_ID: configuration.channelId = parameterValue; break;
            case TOURCMS_PRIVATE_KEY: configuration.apiKey = parameterValue; break;
            case TOURCMS_FILTER_IDS: configuration.filterIds = parameterValue; break;
            case SMTP_SERVER: configuration.smtpServer = parameterValue; break;
            case SMTP_USERNAME: configuration.smtpUsername = parameterValue; break;
            case SMTP_PASSWORD: configuration.smtpPassword = parameterValue; break;
        }
    }

    public static Configuration fromRestParameters(Iterable<io.bokun.inventory.plugin.api.rest.PluginConfigurationParameterValue> configParameters) {
        Configuration configuration = new Configuration();
        for (io.bokun.inventory.plugin.api.rest.PluginConfigurationParameterValue parameterValue : configParameters) {
            setParameterValue(parameterValue.getName(), parameterValue.getValue(), configuration);
        }
        return configuration;
    }

    public String getTourcmsPrivateKey() {
        String tourcmsPrivateKey = System.getenv("TOURCMS_API_KEY");
        if (tourcmsPrivateKey == null) {
            return apiKey;
        }

        return tourcmsPrivateKey;
    }
}
