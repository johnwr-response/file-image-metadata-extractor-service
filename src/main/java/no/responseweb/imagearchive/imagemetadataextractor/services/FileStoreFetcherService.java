package no.responseweb.imagearchive.imagemetadataextractor.services;

import java.util.UUID;

public interface FileStoreFetcherService {
    byte[] fetchFile(UUID fileItemId);
}
