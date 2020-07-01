package no.responseweb.imagearchive.imagemetadataextractor.services;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import no.responseweb.imagearchive.filestoredbservice.domain.ImageMetadataDirectory;
import no.responseweb.imagearchive.filestoredbservice.domain.ImageMetadataTag;
import no.responseweb.imagearchive.filestoredbservice.domain.ImageMetadataValue;
import no.responseweb.imagearchive.filestoredbservice.mappers.FileItemMapper;
import no.responseweb.imagearchive.filestoredbservice.mappers.FilePathMapper;
import no.responseweb.imagearchive.filestoredbservice.mappers.FileStoreMapper;
import no.responseweb.imagearchive.filestoredbservice.repositories.*;
import no.responseweb.imagearchive.imagemetadataextractor.config.JmsConfig;
import no.responseweb.imagearchive.imagemetadataextractor.config.ResponseFileStoreProperties;
import no.responseweb.imagearchive.model.FileItemDto;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Slf4j
@RequiredArgsConstructor
@Component
public class ImageMetadataExtractorJobListener {

    private final FileStoreRepository fileStoreRepository;
    private final FileStoreMapper fileStoreMapper;
    private final FilePathRepository filePathRepository;
    private final FilePathMapper filePathMapper;
    private final FileItemRepository fileItemRepository;
    private final FileItemMapper fileItemMapper;
    private final ImageMetadataCollectionRepository imageMetadataCollectionRepository;
    private final ImageMetadataDirectoryRepository imageMetadataDirectoryRepository;
    private final ImageMetadataTagRepository imageMetadataTagRepository;
    private final ImageMetadataValueRepository imageMetadataValueRepository;
    private final StatusWalkerRepository statusWalkerRepository;

    private final ResponseFileStoreProperties responseFileStoreProperties;

    private final FileStoreFetcherService fileStoreFetcherService;

    @JmsListener(destination = JmsConfig.IMAGE_METADATA_EXTRACTOR_JOB_QUEUE)
    public void listen(FileItemDto fileItemDto) throws IOException, ImageProcessingException {
        log.info("Called with: {}", fileItemDto);
        if (fileItemDto.getId()!=null) {
//            fileItemDto = fileItemMapper.fileItemToFileItemDto(fileItemRepository.findFirstById(fileItemDto.getId()));
            byte[] downloadedBytes = fileStoreFetcherService.fetchFile(fileItemDto.getId());
            log.info("File: {}, Size: {}", fileItemDto.getFilename(), downloadedBytes.length);
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(downloadedBytes));
            if (image!=null) {
                Metadata metadata = ImageMetadataReader.readMetadata(new ByteArrayInputStream(downloadedBytes));
                for (Directory directory : metadata.getDirectories()) {
                    ImageMetadataDirectory currDir = imageMetadataDirectoryRepository.findFirstByName(directory.getName());
                    if (currDir == null) {
                        currDir = imageMetadataDirectoryRepository.save(ImageMetadataDirectory.builder()
                                .collectionId(imageMetadataCollectionRepository.findFirstByName("unset").getId())
                                .name(directory.getName())
                                .build());
                    }
                    for (Tag tag : directory.getTags()) {
                        ImageMetadataTag currTag = imageMetadataTagRepository.findFirstByDirectoryIdAndKeyName(currDir.getId(), tag.getTagName());
                        if (currTag == null) {
                            currTag = imageMetadataTagRepository.save(ImageMetadataTag.builder()
                                    .directoryId(currDir.getId())
                                    .keyName(tag.getTagName())
                                    .tagDec(tag.getTagType())
                                    .build());
                        }
                        ImageMetadataValue currValue = imageMetadataValueRepository.findFirstByTagIdAndFileItemId(currTag.getId(), fileItemDto.getId());
                        if (currValue == null) {
                            currValue = imageMetadataValueRepository.save(ImageMetadataValue.builder()
                                    .tagId(currTag.getId())
                                    .fileItemId(fileItemDto.getId())
                                    .value(tag.getDescription())
                                    .build());
                        } else if (!currValue.getValue().equals(tag.getDescription())) {
                            currValue.setValue(tag.getDescription());
                            imageMetadataValueRepository.save(currValue);
                        }
                        log.info("File.name: {}, Directory.name: {}, Tag.type: {}, Tag.name: {}, Tag.description: {}", fileItemDto.getFilename(), currDir.getName(), currTag.getTagDec(), currTag.getKeyName(), currValue.getValue());
                    }
                }

                if (responseFileStoreProperties.isThumbnailGenerate()) {
                    // create and store thumbnail
                    int configuredThumbSize = responseFileStoreProperties.getThumbnailSize();

                    ByteArrayOutputStream os = new ByteArrayOutputStream();

                    if (image.getHeight()>image.getWidth()) {
                        Thumbnails.of(image)
                                .height(configuredThumbSize)
                                .outputFormat("png")
                                .toOutputStream(os);
                    } else {
                        Thumbnails.of(image)
                                .width(configuredThumbSize)
                                .outputFormat("png")
                                .toOutputStream(os);
                    }
                    fileItemDto.setThumbnail(os.toByteArray());

                    fileItemRepository.save(fileItemMapper.fileItemDtoToFileItem(fileItemDto));
                }

            } else {
                log.info("Not an Image: {}", fileItemDto.getFilename());
            }
        }
    }
}
