/*
 * Copyright 2023-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.aimodel.chat.messages;

import java.io.IOException;
import java.net.URI;

import org.springframework.core.io.Resource;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.FileCopyUtils;
import org.springframework.util.MimeType;

/**
 * Media类表示消息中媒体附件的数据和元数据。它由MIME类型、原始数据和可选元数据（如id和name）组成。
 *
 * <p>
 * 媒体对象可以在UserMessage类中用于附加各种类型的内容，如图像、文档或视频。在与AI模型交互时，id和name字段有助于跟踪和引用特定的媒体对象。
 *
 * <p>
 * id字段通常由AI模型在引用之前提供的媒体时分配。
 *
 * <p>
 * name字段可用于为模型提供描述性标识符，但应注意避免快速注入漏洞。对于amazon AWS，名称只能包含：
 * <ul>
 * <li>字母数字字符
 * <li>空白字符（一行不超过一个）
 * <li>连字符
 * <li>圆括号
 * <li>方括号
 * </ul>
 * 请注意，此类并不直接强制执行该限制。
 *
 * <p>
 * 如果没有提供名称，将使用模式自动生成一个名称：
 * {@code {mimeType.subtype}-{UUID}}
 *
 * <p>
 * 此类包括 {@link Format} 内部类，提供常用的MIME类型作为常量，按内容类别（文档、视频、图像）组织。
 * 在构造Media对象时可以使用这些格式，以确保正确的MIME类型规范。
 *
 * <p>
 * 此类用作UserMessage类构造函数中的参数。
 *
 * @author Christian Tzolov
 * @author Mark Pollack
 * @author Thomas Vitale
 * @since 1.0.0
 */
public class Media {

	private static final String NAME_PREFIX = "media-";

	/**
	 * 媒体对象的Id，通常在模型返回对已传递的media的引用时定义。
	 */
	@Nullable
	private final String id;

	private final MimeType mimeType;

	private final Object data;

	/**
	 * AI模型可以引用的媒体对象的名称。
	 * <p>
	 * 重要的安全提示：此字段容易受到提示注入的影响，因为模型可能会无意中将其解释为指令。建议指定中性名称。
	 * <p>
	 * 名称只能包含：
	 * <ul>
	 * <li>字母数字字符
	 * <li>空白字符（一行不超过一个）
	 * <li>连字符
	 * <li>圆括号
	 * <li>方括号
	 * </ul>
	 */
	private final String name;

	/**
	 * Create a new Media instance.
	 * @param mimeType the media MIME type
	 * @param uri the URI for the media data
	 */
	public Media(MimeType mimeType, URI uri) {
		Assert.notNull(mimeType, "MimeType must not be null");
		Assert.notNull(uri, "URI must not be null");
		this.mimeType = mimeType;
		this.id = null;
		this.data = uri.toString();
		this.name = generateDefaultName(mimeType);
	}

	/**
	 * Create a new Media instance.
	 *
	 * @param mimeType the media MIME type
	 * @param resource the media resource
	 */
	public Media(MimeType mimeType, Resource resource) {
		Assert.notNull(mimeType, "MimeType must not be null");
		Assert.notNull(resource, "Data must not be null");
		try {
			byte[] bytes = FileCopyUtils.copyToByteArray(resource.getInputStream());
			this.mimeType = mimeType;
			this.id = null;
			this.data = bytes;
			this.name = generateDefaultName(mimeType);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Creates a new Media builder.
	 * @return a new Media builder instance
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Create a new Media instance.
	 * @param mimeType the media MIME type
	 * @param data the media data
	 * @param id the media id
	 */
	private Media(MimeType mimeType, Object data, @Nullable String id, @Nullable String name) {
		Assert.notNull(mimeType, "MimeType must not be null");
		Assert.notNull(data, "Data must not be null");
		this.mimeType = mimeType;
		this.id = id;
		this.name = (name != null) ? name : generateDefaultName(mimeType);
		this.data = data;
	}

	private static String generateDefaultName(MimeType mimeType) {
		return NAME_PREFIX + mimeType.getSubtype() + "-" + java.util.UUID.randomUUID();
	}

	/**
	 * Get the media MIME type
	 * @return the media MIME type
	 */
	public MimeType getMimeType() {
		return this.mimeType;
	}

	/**
	 * Get the media data object
	 * @return a java.net.URI.toString() or a byte[]
	 */
	public Object getData() {
		return this.data;
	}

	/**
	 * Get the media data as a byte array
	 * @return the media data as a byte array
	 */
	public byte[] getDataAsByteArray() {
		if (this.data instanceof byte[]) {
			return (byte[]) this.data;
		}
		else {
			throw new IllegalStateException("Media data is not a byte[]");
		}
	}

	/**
	 * Get the media id
	 * @return the media id
	 */
	@Nullable
	public String getId() {
		return this.id;
	}

	public String getName() {
		return this.name;
	}

	/**
	 * Builder class for Media.
	 */
	public static final class Builder {

		private String id;

		private MimeType mimeType;

		private Object data;

		private String name;

		private Builder() {
		}

		/**
		 * Sets the MIME type for the media object.
		 * @param mimeType the media MIME type, must not be null
		 * @return the builder instance
		 * @throws IllegalArgumentException if mimeType is null
		 */
		public Builder mimeType(MimeType mimeType) {
			Assert.notNull(mimeType, "MimeType must not be null");
			this.mimeType = mimeType;
			return this;
		}

		/**
		 * Sets the media data from any Object.
		 * @param data the media data object, must not be null
		 * @return the builder instance
		 * @throws IllegalArgumentException if data is null
		 */
		public Builder data(Object data) {
			Assert.notNull(data, "Data must not be null");
			this.data = data;
			return this;
		}

		/**
		 * Sets the media data from a URI.
		 * @param uri the media URI, must not be null
		 * @return the builder instance
		 * @throws IllegalArgumentException if URI is null
		 */
		public Builder data(URI uri) {
			Assert.notNull(uri, "URI must not be null");
			this.data = uri.toString();
			return this;
		}

		/**
		 * Sets the ID for the media object. The ID is typically assigned by AI models
		 * when they return a reference to previously provided media content.
		 * @param id the media identifier
		 * @return the builder instance
		 */
		public Builder id(String id) {
			this.id = id;
			return this;
		}

		/**
		 * Sets the name for the media object.
		 * <p>
		 * Important security note: This field is vulnerable to prompt injections, as the
		 * model might inadvertently interpret it as instructions. It is recommended to
		 * specify neutral names.
		 *
		 * <p>
		 * The name must only contain:
		 * <ul>
		 * <li>Alphanumeric characters
		 * <li>Whitespace characters (no more than one in a row)
		 * <li>Hyphens
		 * <li>Parentheses
		 * <li>Square brackets
		 * </ul>
		 * @param name the media name
		 * @return the builder instance
		 */
		public Builder name(String name) {
			this.name = name;
			return this;
		}

		/**
		 * Builds a new Media instance with the configured properties.
		 * @return a new Media instance
		 * @throws IllegalArgumentException if mimeType or data are null
		 */
		public Media build() {
			return new Media(this.mimeType, this.data, this.id, this.name);
		}

	}

	/**
	 * Common media formats.
	 */
	public static class Format {

		// -----------------
		// Document formats
		// -----------------
		/**
		 * Public constant mime type for {@code application/pdf}.
		 */
		public static final MimeType DOC_PDF = MimeType.valueOf("application/pdf");

		/**
		 * Public constant mime type for {@code text/csv}.
		 */
		public static final MimeType DOC_CSV = MimeType.valueOf("text/csv");

		/**
		 * Public constant mime type for {@code application/msword}.
		 */
		public static final MimeType DOC_DOC = MimeType.valueOf("application/msword");

		/**
		 * Public constant mime type for
		 * {@code application/vnd.openxmlformats-officedocument.wordprocessingml.document}.
		 */
		public static final MimeType DOC_DOCX = MimeType
			.valueOf("application/vnd.openxmlformats-officedocument.wordprocessingml.document");

		/**
		 * Public constant mime type for {@code application/vnd.ms-excel}.
		 */
		public static final MimeType DOC_XLS = MimeType.valueOf("application/vnd.ms-excel");

		/**
		 * Public constant mime type for
		 * {@code application/vnd.openxmlformats-officedocument.spreadsheetml.sheet}.
		 */
		public static final MimeType DOC_XLSX = MimeType
			.valueOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

		/**
		 * Public constant mime type for {@code text/html}.
		 */
		public static final MimeType DOC_HTML = MimeType.valueOf("text/html");

		/**
		 * Public constant mime type for {@code text/plain}.
		 */
		public static final MimeType DOC_TXT = MimeType.valueOf("text/plain");

		/**
		 * Public constant mime type for {@code text/markdown}.
		 */
		public static final MimeType DOC_MD = MimeType.valueOf("text/markdown");

		// -----------------
		// Video Formats
		// -----------------
		/**
		 * Public constant mime type for {@code video/x-matros}.
		 */
		public static final MimeType VIDEO_MKV = MimeType.valueOf("video/x-matros");

		/**
		 * Public constant mime type for {@code video/quicktime}.
		 */
		public static final MimeType VIDEO_MOV = MimeType.valueOf("video/quicktime");

		/**
		 * Public constant mime type for {@code video/mp4}.
		 */
		public static final MimeType VIDEO_MP4 = MimeType.valueOf("video/mp4");

		/**
		 * Public constant mime type for {@code video/webm}.
		 */
		public static final MimeType VIDEO_WEBM = MimeType.valueOf("video/webm");

		/**
		 * Public constant mime type for {@code video/x-flv}.
		 */
		public static final MimeType VIDEO_FLV = MimeType.valueOf("video/x-flv");

		/**
		 * Public constant mime type for {@code video/mpeg}.
		 */
		public static final MimeType VIDEO_MPEG = MimeType.valueOf("video/mpeg");

		/**
		 * Public constant mime type for {@code video/mpeg}.
		 */
		public static final MimeType VIDEO_MPG = MimeType.valueOf("video/mpeg");

		/**
		 * Public constant mime type for {@code video/x-ms-wmv}.
		 */
		public static final MimeType VIDEO_WMV = MimeType.valueOf("video/x-ms-wmv");

		/**
		 * Public constant mime type for {@code video/3gpp}.
		 */
		public static final MimeType VIDEO_THREE_GP = MimeType.valueOf("video/3gpp");

		// -----------------
		// Image Formats
		// -----------------
		/**
		 * Public constant mime type for {@code image/png}.
		 */
		public static final MimeType IMAGE_PNG = MimeType.valueOf("image/png");

		/**
		 * Public constant mime type for {@code image/jpeg}.
		 */
		public static final MimeType IMAGE_JPEG = MimeType.valueOf("image/jpeg");

		/**
		 * Public constant mime type for {@code image/gif}.
		 */
		public static final MimeType IMAGE_GIF = MimeType.valueOf("image/gif");

		/**
		 * Public constant mime type for {@code image/webp}.
		 */
		public static final MimeType IMAGE_WEBP = MimeType.valueOf("image/webp");

	}

}