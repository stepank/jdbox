package jdbox;

import com.google.api.services.drive.Drive;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import net.fusejna.DirectoryFiller;
import net.fusejna.ErrorCodes;
import net.fusejna.StructFuseFileInfo;
import net.fusejna.StructStat;
import net.fusejna.types.TypeMode;
import net.fusejna.util.FuseFilesystemAdapterFull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class FileSystem extends FuseFilesystemAdapterFull {

    private static final Set<String> nonExistentFiles = new HashSet<String>() {{
        add("/.Trash");
        add("/.Trash-1000");
        add("/.xdg-volume-info");
        add("/autorun.inf");
    }};

    private static final Map<String, String> fileFormatNames = new HashMap<String, String>() {{
        put("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx");
        put("application/vnd.oasis.opendocument.text", "odt");
        put("application/rtf", "rtf");
        put("text/html", "html");
        put("text/plain", "txt");
        put("application/pdf", "pdf");
    }};

    private static final Logger logger = LoggerFactory.getLogger(FileSystem.class);

    private final DriveAdapter drive;
    private final FileInfoResolver fileInfoResolver;

    private final LoadingCache<String, NavigableMap<String, File>> fileListCache;

    public FileSystem(Drive drive) {

        this.drive = new DriveAdapter(drive);
        fileInfoResolver = new FileInfoResolver(this.drive);

        fileListCache = CacheBuilder.newBuilder()
                .expireAfterWrite(5, TimeUnit.SECONDS)
                .build(
                        new CacheLoader<String, NavigableMap<String, File>>() {
                            @Override
                            public NavigableMap<String, File> load(String path) throws Exception {
                                File dir = fileInfoResolver.get(path);
                                NavigableMap<String, File> files = new TreeMap<>();
                                for (File file : FileSystem.this.drive.getChildren(dir.getId())) {
                                    String name = file.getName();
                                    files.put(name, file);
                                    fileInfoResolver.put(
                                            path.equals(java.io.File.separator) ?
                                                    path + name : path + java.io.File.separator + name,
                                            file);
                                }
                                return files;
                            }
                        }
                );
    }

    @Override
    public int getattr(final String path, final StructStat.StatWrapper stat) {

        logger.debug("getting attrs of {}", path);

        if (nonExistentFiles.contains(path))
            return -ErrorCodes.ENOENT();

        try {

            File file = fileInfoResolver.get(path);

            if (file == null)
                return -ErrorCodes.ENOENT();

            if (file.isDirectory())
                stat.setMode(TypeMode.NodeType.DIRECTORY, true, true, true, true, true, true, true, false, true);
            else
                stat.setMode(TypeMode.NodeType.FILE);

            stat.size(file.getSize());

        } catch (Exception e) {
            logger.error("an error occurred while getting attrs", e);
        }

        return 0;
    }

    @Override
    public int readdir(final String path, final DirectoryFiller filler) {

        logger.debug("reading directory {}", path);

        try {
            for (File file : fileListCache.get(path).values()) {
                filler.add(file.getName());
            }
        } catch (Exception e) {
            logger.error("an error occured while reading directory from cache", e);
        }

        return 0;
    }

    @Override
    public int read(String path, ByteBuffer buffer, long count, long offset, StructFuseFileInfo.FileInfoWrapper info) {

        logger.debug("reading file {}, offset {}, count {}", path, offset, count);

        try {

            File file = fileInfoResolver.get(path);

            if (!file.isDownloadable()) {

                Map<String, String> links = file.getExportLinks();
                if (links == null)
                    return 0;

                StringBuilder builder = new StringBuilder();
                builder.append(
                        "This file cannot be downloaded directly. You can use one of the following links to export it:\n");
                for (Map.Entry<String, String> link : links.entrySet()) {
                    String formatName = fileFormatNames.get(link.getKey());
                    if (formatName == null)
                        formatName = link.getKey();
                    builder.append(formatName).append(" - ").append(link.getValue()).append("\n");
                }

                buffer.put(Arrays.copyOfRange(builder.toString().getBytes(), (int) offset, (int) (offset + count)));
                return (int) Math.min(count, builder.length() - offset);
            }

            InputStream stream = drive.downloadFileRange(file, offset, count);

            byte[] bytes = new byte[4096];
            int read, total = 0;
            while ((read = stream.read(bytes, 0, bytes.length)) != -1) {
                buffer.put(bytes, 0, read);
                total += read;
            }

            return total;

        } catch (Exception e) {
            logger.error("an error occurred while reading file", e);
            return 0;
        }
    }
}