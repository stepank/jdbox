package jdbox;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.util.ByteStreams;
import com.google.api.services.drive.Drive;
import net.fusejna.DirectoryFiller;

import java.io.InputStream;
import java.util.*;

/**
 * These results were obtained on a 12Mbits/s channel:
 * <p/>
 * >>> offset,fetch,prefetch,w50p,w80p,r50p,r80p
 * >>> 0,128,1024,708,876,110,183
 * >>> 0,128,2048,1047,3777,99,157
 * >>> 0,128,4096,894,1322,94,161
 * >>> 4096,128,1024,675,843,100,176
 * >>> 4096,128,2048,774,1187,96,160
 * >>> 4096,128,4096,898,1597,94,125
 * <p/>
 * It seems clear that even in the case of a network that is so slow, prefetch sizes smaller than 4MB are useless.
 * The thing is that if we request the next page after having read the first half of the current page,
 * the request for the next page will not have been completed by the time the current page is read up to its end.
 * This will intoroduce a delay before next page after a small one can be read, which is undesirable.
 */
public class Benchmark {

    public static void main(String[] args) throws Exception {

        if (args.length < 2) {
            System.out.println("error: Usage is jdbox.Benchmark <test-dir-path> <suffix>");
        }

        String path = args[0];
        String suffix = args[1];

        Drive drive = JdBox.getDriveService(
                new java.io.File(System.getProperty("user.home") + "/.jdbox"));

        FileSystem fileSystem = new FileSystem(drive);

        final List<String> fileNames = new LinkedList<>();
        fileSystem.readdir(path, new DirectoryFiller() {
            @Override
            public boolean add(Iterable<String> files) {
                for (String file : files) {
                    fileNames.add(file);
                }
                return true;
            }

            @Override
            public boolean add(String... files) {
                Collections.addAll(fileNames, files);
                return true;
            }
        });

        final FileInfoResolver fileInfoResolver = fileSystem.getFileInfoResolver();

        List<File> files = new LinkedList<>();
        for (String fileName : fileNames) {
            if (fileName.toLowerCase().endsWith(suffix.toLowerCase()))
                files.add(fileInfoResolver.get(path + java.io.File.separator + fileName));
        }

        int[] offsets = new int[]{0, 4096};
        int[] fetchSizes = new int[]{128};
        int[] prefetchSizes = new int[]{1024, 2048, 4096};

        System.out.println(">>> offset,fetch,prefetch,w50p,w80p,r50p,r80p");

        for (int offset : offsets) {
            offset *= 1024;

            for (int fetchSize : fetchSizes) {
                fetchSize *= 1024;

                for (int prefetchSize : prefetchSizes) {
                    prefetchSize *= 1024;

                    if (fetchSize > prefetchSize)
                        continue;

                    ArrayList<Long> warmups = new ArrayList<>();
                    ArrayList<Long> reads = new ArrayList<>();
                    for (File file : files) {


                        try {

                            System.out.println("downloading file " + file.getName());
                            HttpRequest request = drive.getRequestFactory().buildGetRequest(new GenericUrl(file.getDownloadUrl()));
                            request.getHeaders().setRange(String.format("bytes=%s-%s", offset, offset + prefetchSize - 1));

                            Date start = new Date();
                            HttpResponse response = request.execute();
                            InputStream stream = response.getContent();
                            warmups.add(new Date().getTime() - start.getTime());

                            byte[] buffer = new byte[fetchSize];
                            for (int i = 0; i < prefetchSize / fetchSize; i++) {
                                start = new Date();
                                ByteStreams.read(stream, buffer, 0, fetchSize);
                                reads.add(new Date().getTime() - start.getTime());
                            }

                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                    Collections.sort(warmups);
                    Collections.sort(reads);

                    System.out.println(
                            String.format(
                                    ">>> %s,%s,%s,%s,%s,%s,%s",
                                    offset / 1024, fetchSize / 1024, prefetchSize / 1024,
                                    warmups.get(warmups.size() / 2), warmups.get(warmups.size() * 4 / 5),
                                    reads.get(reads.size() / 2), reads.get(reads.size() * 4 / 5)));
                }
            }
        }
    }
}
