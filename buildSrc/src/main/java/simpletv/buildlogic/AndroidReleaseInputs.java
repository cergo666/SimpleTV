package simpletv.buildlogic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.Locale;
import java.util.regex.Pattern;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

@CacheableTask
public abstract class AndroidReleaseInputs extends DefaultTask {
    private static final String LABEL = "[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?";
    private static final Pattern HOST = Pattern.compile(LABEL + "(?:\\." + LABEL + ")+");

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getPlaylistsDirectory();

    @Input public abstract Property<String> getTlsPins();
    @Input public abstract Property<String> getSigner();
    @OutputDirectory public abstract DirectoryProperty getAssetsDirectory();
    @OutputDirectory public abstract DirectoryProperty getResourcesDirectory();
    @Inject protected abstract FileSystemOperations getFileSystemOperations();

    @TaskAction
    public void generate() throws IOException {
        String securityConfig = securityConfig(getTlsPins().get());
        Path assets = getAssetsDirectory().get().getAsFile().toPath();
        Path resources = getResourcesDirectory().get().getAsFile().toPath();
        getFileSystemOperations().delete(spec -> spec.delete(assets.toFile(), resources.toFile()));
        Path playlists = Files.createDirectories(assets.resolve("playlists"));
        Path xml = Files.createDirectories(resources.resolve("xml"));
        byte[] signer = EncodedLiteral.signerBytes(getSigner().get());
        try (var paths = Files.list(getPlaylistsDirectory().get().getAsFile().toPath())) {
            for (Path path : paths.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".m3u")).sorted().toList()) {
                byte[] bytes = Files.readAllBytes(path);
                String name = path.getFileName().toString();
                Files.writeString(playlists.resolve(name + ".stv"),
                        EncodedLiteral.encode(bytes, 31 * name.hashCode() + Arrays.hashCode(bytes), signer));
            }
        }
        Files.writeString(xml.resolve("network_security_config_release.xml"), securityConfig);
    }

    private static String securityConfig(String value) {
        var xml = new StringBuilder("""
                <?xml version="1.0" encoding="utf-8"?>
                <network-security-config>
                  <base-config cleartextTrafficPermitted="true">
                    <trust-anchors>
                      <certificates src="system" />
                    </trust-anchors>
                  </base-config>
                """);
        var hosts = new HashSet<String>();
        for (String entry : value.split("[;\\r\\n]+")) {
            if (entry.isBlank()) continue;
            String[] parts = entry.trim().split("=", 2);
            String host = parts[0].trim().toLowerCase(Locale.ROOT);
            if (parts.length != 2 || host.length() > 253 || !HOST.matcher(host).matches() || !hosts.add(host)) {
                throw new IllegalArgumentException("Expected unique TLS pin entries in host=primary,backup format.");
            }
            var pins = new HashSet<String>();
            xml.append("  <domain-config cleartextTrafficPermitted=\"false\">\n")
               .append("    <domain includeSubdomains=\"false\">").append(host).append("</domain>\n")
               .append("    <trust-anchors><certificates src=\"system\" /></trust-anchors>\n")
               .append("    <pin-set>\n");
            for (String candidate : parts[1].split(",", -1)) {
                byte[] digest = Base64.getDecoder().decode(candidate.trim().replaceFirst("^sha256/", ""));
                String pin = Base64.getEncoder().encodeToString(digest);
                if (digest.length != 32 || !pins.add(pin)) {
                    throw new IllegalArgumentException("Expected distinct SHA-256 SPKI pins for " + host);
                }
                xml.append("      <pin digest=\"SHA-256\">").append(pin).append("</pin>\n");
            }
            if (pins.size() < 2) throw new IllegalArgumentException("A backup TLS pin is required for " + host);
            xml.append("    </pin-set>\n  </domain-config>\n");
        }
        return xml.append("</network-security-config>\n").toString();
    }
}
