package me.meow.sniper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.annotations.SerializedName;
import okhttp3.*;
import org.javacord.api.AccountType;
import org.javacord.api.DiscordApiBuilder;
import org.javacord.api.event.message.MessageCreateEvent;
import org.javacord.api.listener.message.MessageCreateListener;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * pxl.blue invite sniper.
 * For educational purposes only.
 * Selfbots are against <a href="https://discord.com/guidelines">Discord's Community Guidelines</a>.
 * Use at your own risk.
 *
 * @author meow
 * @since 3/3/2021
 * @version 1
 */
public enum Main implements MessageCreateListener {
    INSTANCE;

    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static final OkHttpClient client = new OkHttpClient();
    private static final Pattern inviteRegex = Pattern.compile("\\b[a-f0-9]{40}\\b");
    private static final PrintStream out = System.out;
    private static Config config;

    public static void main(String[] args) throws IOException {
        final PrintStream deadStream = new PrintStream(new OutputStream() {
            @Override public void write(int b) {}
            @Override public void write(byte[] b) {}
            @Override public void write(byte[] b, int off, int len) {}
        });

        System.setOut(deadStream);
        System.setErr(deadStream);

        final File configFile = new File("config.json");
        if (configFile.exists()) {
            config = gson.fromJson(new String(Files.readAllBytes(configFile.toPath())), Config.class);

            out.printf("Logged in: %s%n", new DiscordApiBuilder().setAccountType(AccountType.CLIENT).setToken(config.token).addMessageCreateListener(INSTANCE).login().join().getYourself().getDiscriminatedName());
        } else {
            Files.write(configFile.toPath(), gson.toJson(new Config("Discord token here", "Registration username here", "Registration email here", "Registration password here")).getBytes(StandardCharsets.UTF_8));
            out.println("Created config");
        }
    }

    @Override
    public void onMessageCreate(final MessageCreateEvent event) {
        final Matcher matcher = inviteRegex.matcher(event.getMessageContent().replace("`", " ").replace("*", " ").replace("_", " "));

        if (matcher.find()) {
            final String code = matcher.group();

            final JsonObject json = gson.toJsonTree(config).getAsJsonObject();
            json.remove("token");
            json.addProperty("invite", code);
            final String content = json.toString();

            try (final Response response = client.newCall(new Request.Builder().url("https://api.pxl.blue/auth/register").method("POST", RequestBody.create(MediaType.parse("application/json"), content)).addHeader("Content-Type", "application/json").addHeader("Content-Length", String.valueOf(content.length())).build()).execute()) {
                final ResponseBody body = response.body();

                if (body != null) {
                    final JsonObject object = JsonParser.parseString(body.string()).getAsJsonObject();
                    if (object.get("success").getAsBoolean()) out.printf("Registered successfully. Code: %s, Link: %s%n", code, event.getMessage().getLink());
                    else if (object.has("errors")) out.printf("Failed to register. Code: %s, Response: %s, Link: %s%n", code, object.get("errors").getAsJsonArray().get(0).getAsString(), event.getMessage().getLink());
                    else out.printf("Failed to register. Code: %s, Link: %s%n", code, event.getMessage().getLink());
                }
            } catch (IOException e) {
                out.printf("Failed to make request. Code: %s, Message: %s, Link: %s%n", code, e.getMessage(), event.getMessage().getLink());
            }
        }
    }

    private static final class Config {
        @SerializedName("token")
        private final String token;
        @SerializedName("username")
        private final String username;
        @SerializedName("email")
        private final String email;
        @SerializedName("password")
        private final String password;

        public Config(String token, String username, String email, String password) {
            this.token = token;
            this.username = username;
            this.email = email;
            this.password = password;
        }
    }
}
