package org.openhomeo.core.net;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.openhomeo.core.image.ImageVaultService;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Map;

/**
 * Network-proxied Clinical Image Vault for CLIENT workstations.
 * Stores and retrieves encrypted clinical dermatology images directly on the shared Clinic Server.
 */
public class RemoteImageVaultService extends ImageVaultService {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private final String serverBaseUrl;
    private final String pairingKey;

    public RemoteImageVaultService(String localCacheDir, String serverBaseUrl, String pairingKey) {
        super(localCacheDir);
        this.serverBaseUrl = serverBaseUrl.endsWith("/") ? serverBaseUrl.substring(0, serverBaseUrl.length() - 1) : serverBaseUrl;
        this.pairingKey = pairingKey != null ? pairingKey : "";
    }

    @Override
    public StoreResult storeImage(byte[] imageBytes, String formatHint) throws IOException {
        try {
            String fmt = (formatHint != null && !formatHint.isBlank()) ? formatHint : "JPEG";
            HttpRequest.Builder req = HttpRequest.newBuilder()
                    .uri(URI.create(serverBaseUrl + "/clinic-rpc/vault/store?format=" + fmt))
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(imageBytes));
            if (!pairingKey.isBlank()) req.header("X-OpenHomeo-Key", pairingKey);

            HttpResponse<String> resp = HTTP.send(req.build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 400) {
                throw new IOException("Server vault upload failed (" + resp.statusCode() + "): " + resp.body());
            }
            Map<String, Object> m = MAPPER.readValue(resp.body(), new TypeReference<>() {});
            return new StoreResult(
                    String.valueOf(m.get("contentHash")),
                    ((Number) m.get("width")).intValue(),
                    ((Number) m.get("height")).intValue(),
                    String.valueOf(m.get("fileFormat")),
                    ((Number) m.get("fileSizeBytes")).longValue()
            );
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Remote image upload failed: " + e.getMessage(), e);
        }
    }

    @Override
    public byte[] retrieveImage(String contentHash) throws IOException {
        try {
            HttpRequest.Builder req = HttpRequest.newBuilder()
                    .uri(URI.create(serverBaseUrl + "/clinic-rpc/vault/image?hash=" + contentHash))
                    .timeout(Duration.ofSeconds(30))
                    .GET();
            if (!pairingKey.isBlank()) req.header("X-OpenHomeo-Key", pairingKey);

            HttpResponse<byte[]> resp = HTTP.send(req.build(), HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() != 200) {
                throw new IOException("Image not found on Clinic Server (" + resp.statusCode() + "): " + contentHash);
            }
            return resp.body();
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Remote image retrieve failed: " + e.getMessage(), e);
        }
    }

    @Override
    public File getThumbnailFile(String contentHash) {
        File localThumb = super.getThumbnailFile(contentHash);
        if (localThumb.exists()) return localThumb;
        try {
            HttpRequest.Builder req = HttpRequest.newBuilder()
                    .uri(URI.create(serverBaseUrl + "/clinic-rpc/vault/thumb?hash=" + contentHash))
                    .timeout(Duration.ofSeconds(15))
                    .GET();
            if (!pairingKey.isBlank()) req.header("X-OpenHomeo-Key", pairingKey);
            HttpResponse<byte[]> resp = HTTP.send(req.build(), HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() == 200 && resp.body() != null && resp.body().length > 0) {
                if (localThumb.getParentFile() != null) localThumb.getParentFile().mkdirs();
                Files.write(localThumb.toPath(), resp.body());
            }
        } catch (Exception ignored) {}
        return localThumb;
    }
}
