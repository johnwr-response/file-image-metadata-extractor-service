package no.responseweb.imagearchive.imagemetadataextractor;

import no.responseweb.imagearchive.filestoredbservice.config.DBModuleConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(DBModuleConfig.class)
@EnableFeignClients
public class ImageMetadataExtractorApplication {

	public static void main(String[] args) {
		SpringApplication.run(ImageMetadataExtractorApplication.class, args);
	}

}


