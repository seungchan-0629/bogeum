package com.bogeum.utils;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Set;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import com.bogeum.common.exception.ImageException;

/** 실제 이미지 바이트를 변환·재디코딩해 형식·크기·투명 배경 처리와 잘못된 파일 거절을 확인한다. */
class ImageProcessorTest {
	private final ImageProcessor processor = new ImageProcessor(20971520, 40000000, 2048, 0.85f, Set.of("JPEG", "PNG"));

	@Test
	void decodesActualContentResizesAndCompositesTransparency() throws Exception {
		var original = new BufferedImage(3000, 1000, BufferedImage.TYPE_INT_ARGB);
		var bytes = new ByteArrayOutputStream();
		ImageIO.write(original, "PNG", bytes);
		// 파일명과 MIME이 달라도 실제 내용이 허용된 PNG이면 처리하는지 확인한다.
		byte[] jpeg = processor.process(new MockMultipartFile("images", "ignored.txt", "text/plain", bytes.toByteArray()));
		var output = ImageIO.read(new ByteArrayInputStream(jpeg));
		assertEquals(2048, output.getWidth());
		assertEquals(682, output.getHeight());
		assertEquals(0xffffff, output.getRGB(0, 0) & 0xffffff);
		assertEquals(0xff, jpeg[0] & 0xff);
		assertEquals(0xd8, jpeg[1] & 0xff);
	}

	@Test
	void rejectsFakeEmptyAndOversizeImages() {
		for (byte[] bytes : new byte[][]{new byte[0], "not an image".getBytes(), new byte[20971521]}) {
			assertThrows(ImageException.class, () -> processor.process(new MockMultipartFile("images", "fake.jpg", "image/jpeg", bytes)));
		}
	}

	@Test
	void rejectsUnsupportedContentAndPixelLimit() throws Exception {
		var bytes = new ByteArrayOutputStream();
		ImageIO.write(new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB), "GIF", bytes);
		assertThrows(ImageException.class, () -> processor.process(new MockMultipartFile("images", bytes.toByteArray())));
		var smallLimit = new ImageProcessor(10000, 50, 2048, 0.85f, Set.of("PNG"));
		var png = new ByteArrayOutputStream();
		ImageIO.write(new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB), "PNG", png);
		assertThrows(ImageException.class, () -> smallLimit.process(new MockMultipartFile("images", png.toByteArray())));
	}
}
