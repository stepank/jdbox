package jdbox;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.util.ByteStreams;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Date;

public class Benchmark {

    public static void main(String[] args) throws Exception {

        String id = "0Bw5v2b28KfpWQmFEUW9qX1RPcEU";

        int chunkSize = 1024 * 1024;
        int start = 0;
        int end = 32 * chunkSize;
        int reportThreshold = 30;

        InputStream content = new FileInputStream("/home/stepank/Downloads/house.mkv");
        assert content.skip(start) == start;
        byte[] original = new byte[end];
        assert content.read(original) == end;
        content.close();

        Drive drive = JdBox.createDriveService(new Environment());

        File file = drive.files().get(id).execute();

        HttpRequest request = drive.getRequestFactory().buildGetRequest(new GenericUrl(file.getDownloadUrl()));
        request.getHeaders().setRange(String.format("bytes=%s-%s", start, end - 1));
        long startedAt = new Date().getTime();
        HttpResponse response = request.execute();
        System.out.println(String.format("execute: %s", new Date().getTime() - startedAt));

        startedAt = new Date().getTime();
        content = response.getContent();
        System.out.println(String.format("get content: %s", new Date().getTime() - startedAt));

        startedAt = new Date().getTime();
        byte[] chunk = new byte[chunkSize];
        for (int i = 0; i < end / chunkSize; i++) {

            long s = new Date().getTime();
            assert ByteStreams.read(content, chunk, 0, chunkSize) == chunkSize;
            long spent = new Date().getTime() - s;

            byte[] expected = Arrays.copyOfRange(original, i * chunkSize, (i + 1) * chunkSize);
            assert Arrays.equals(chunk, expected);

            if (spent > reportThreshold)
                System.out.println(String.format("chunk %s: %s", i, spent));
        }

        System.out.println(String.format("full read: %s", new Date().getTime() - startedAt));
    }
}
