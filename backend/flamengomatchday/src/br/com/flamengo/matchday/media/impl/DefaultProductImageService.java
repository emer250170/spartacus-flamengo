package br.com.flamengo.matchday.media.impl;

import br.com.flamengo.matchday.media.ProductImageService;
import de.hybris.platform.catalog.model.CatalogVersionModel;
import de.hybris.platform.core.model.media.MediaContainerModel;
import de.hybris.platform.core.model.media.MediaFolderModel;
import de.hybris.platform.core.model.media.MediaFormatModel;
import de.hybris.platform.core.model.media.MediaModel;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.servicelayer.config.ConfigurationService;
import de.hybris.platform.servicelayer.media.MediaService;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class DefaultProductImageService implements ProductImageService
{
	private static final Logger LOG = LoggerFactory.getLogger(DefaultProductImageService.class);
	/** Final do código do conjunto de mídias que esta cópia cria no produto */
	protected static final String CONTAINER_SUFFIX = "-fla-img";
	private static final Pattern SIZE = Pattern.compile("(\\d+)Wx(\\d+)H");
	private static final int MAX_UPLOAD_BYTES = 5 * 1024 * 1024;

	private ConfigurationService configurationService;
	private MediaService mediaService;
	private ModelService modelService;
	private FlexibleSearchService flexibleSearchService;
	/** Atualiza a busca do produto depois da foto nova (07/11); opcional */
	private ProductSearchIndexRefresher searchIndexRefresher;

	// ------------------------------------------------------------ envio pelo Backoffice

	@Override
	public MediaModel createUpload(final String prefix, final String fileName, final String mime, final byte[] data)
	{
		if (data == null || data.length == 0)
		{
			throw new IllegalArgumentException("O arquivo está vazio.");
		}
		if (data.length > MAX_UPLOAD_BYTES)
		{
			throw new IllegalArgumentException("A imagem tem mais de 5 MB. Reduza o arquivo e envie de novo.");
		}
		final BufferedImage image = read(data);
		if (image == null)
		{
			throw new IllegalArgumentException("O arquivo não é uma imagem JPG, PNG ou GIF.");
		}
		final MediaModel media = modelService.create(MediaModel.class);
		final SimpleDateFormat stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT);
		stamp.setTimeZone(TimeZone.getTimeZone("America/Sao_Paulo"));
		media.setCode(StringUtils.defaultIfBlank(prefix, "imagem") + "-img-" + stamp.format(new Date()) + "-"
				+ UUID.randomUUID().toString().substring(0, 8));
		media.setRealFileName(StringUtils.defaultIfBlank(fileName, media.getCode() + extension(mime)));
		media.setMime(StringUtils.defaultIfBlank(mime, "image/jpeg"));
		media.setAltText(image.getWidth() + " x " + image.getHeight() + " px");
		final MediaFolderModel folder = folder();
		if (folder != null)
		{
			media.setFolder(folder);
		}
		modelService.save(media);
		mediaService.setDataForMedia(media, data);
		LOG.info("Imagem {} gravada ({} x {} px, {} bytes)", media.getCode(), image.getWidth(), image.getHeight(), data.length);
		return media;
	}

	// ------------------------------------------------------------ cópia para o produto

	@Override
	public String copyToProduct(final MediaModel image, final ProductModel product)
	{
		if (product == null || !configurationService.getConfiguration().getBoolean(COPY_ENABLED_PROPERTY, true))
		{
			return null;
		}
		final String containerCode = product.getCode() + CONTAINER_SUFFIX;
		final CatalogVersionModel catalogVersion = product.getCatalogVersion();
		MediaContainerModel container = findContainer(containerCode, catalogVersion);
		if (image == null)
		{
			return container == null ? null : removeFromProduct(product, container);
		}
		final byte[] original = mediaService.getDataFromMedia(image);
		final BufferedImage source = read(original);
		if (container == null)
		{
			container = modelService.create(MediaContainerModel.class);
			container.setQualifier(containerCode);
			container.setCatalogVersion(catalogVersion);
			modelService.save(container);
		}
		final MediaFolderModel folder = folder();
		MediaModel picture = null;
		MediaModel thumbnail = null;
		final String pictureFormat = config("flamengo.produtos.imagem.formato.principal", "300Wx300H");
		final String thumbnailFormat = config("flamengo.produtos.imagem.formato.miniatura", "96Wx96H");
		for (final String qualifier : formats())
		{
			final MediaFormatModel format = format(qualifier);
			if (format == null)
			{
				continue;
			}
			final String code = containerCode + "-" + qualifier;
			MediaModel media = findMedia(code, catalogVersion);
			if (media == null)
			{
				media = modelService.create(MediaModel.class);
				media.setCode(code);
				media.setCatalogVersion(catalogVersion);
			}
			final boolean png = "image/png".equalsIgnoreCase(image.getMime());
			final byte[] data = source == null ? original : resize(source, size(qualifier), png, quality());
			media.setMediaFormat(format);
			media.setMediaContainer(container);
			media.setMime(source == null ? image.getMime() : png ? "image/png" : "image/jpeg");
			media.setRealFileName(code + (source == null ? extension(image.getMime()) : png ? ".png" : ".jpg"));
			media.setAltText(product.getName(new Locale("pt")));
			if (folder != null)
			{
				media.setFolder(folder);
			}
			modelService.save(media);
			mediaService.setDataForMedia(media, data);
			if (qualifier.equals(pictureFormat))
			{
				picture = media;
			}
			if (qualifier.equals(thumbnailFormat))
			{
				thumbnail = media;
			}
		}
		modelService.refresh(container);
		product.setPicture(picture);
		product.setThumbnail(thumbnail);
		final List<MediaContainerModel> gallery = new ArrayList<>();
		gallery.add(container);
		for (final MediaContainerModel other : safe(product.getGalleryImages()))
		{
			if (!containerCode.equals(other.getQualifier()))
			{
				gallery.add(other);
			}
		}
		product.setGalleryImages(gallery);
		modelService.save(product);
		refreshSearch(product);
		LOG.info("Imagem {} copiada para o produto {} ({})", image.getCode(), product.getCode(), catalogVersion.getVersion());
		return "Imagem copiada para o produto " + product.getCode() + ".";
	}

	protected String removeFromProduct(final ProductModel product, final MediaContainerModel container)
	{
		boolean changed = false;
		if (product.getPicture() != null && container.equals(product.getPicture().getMediaContainer()))
		{
			product.setPicture(null);
			changed = true;
		}
		if (product.getThumbnail() != null && container.equals(product.getThumbnail().getMediaContainer()))
		{
			product.setThumbnail(null);
			changed = true;
		}
		final List<MediaContainerModel> gallery = new ArrayList<>(safe(product.getGalleryImages()));
		if (gallery.remove(container))
		{
			product.setGalleryImages(gallery);
			changed = true;
		}
		if (!changed)
		{
			return null;
		}
		modelService.save(product);
		refreshSearch(product);
		return "Imagem retirada do produto " + product.getCode() + ".";
	}

	// ------------------------------------------------------------ fotos do cadastro de produto (08/11)

	@Override
	public String setProductPhotos(final ProductModel product, final List<MediaModel> photos)
	{
		if (product == null || photos == null || photos.stream().allMatch(java.util.Objects::isNull))
		{
			return null;
		}
		final CatalogVersionModel catalogVersion = product.getCatalogVersion();
		final String pictureFormat = config("flamengo.produtos.imagem.formato.principal", "300Wx300H");
		final String thumbnailFormat = config("flamengo.produtos.imagem.formato.miniatura", "96Wx96H");
		int count = 0;
		for (int position = 0; position < Math.min(4, photos.size()); position++)
		{
			final MediaModel image = photos.get(position);
			if (image == null)
			{
				continue;
			}
			final Map<String, MediaModel> byFormat = fillContainer(image, product, product.getCode() + PHOTO_CONTAINER_SUFFIX + position);
			count++;
			if (position == 0)
			{
				if (byFormat.get(pictureFormat) != null)
				{
					product.setPicture(byFormat.get(pictureFormat));
				}
				if (byFormat.get(thumbnailFormat) != null)
				{
					product.setThumbnail(byFormat.get(thumbnailFormat));
				}
			}
		}
		// galeria: as fotos do cadastro (0 a 3) na frente, na ordem, e depois as que o produto já tinha
		final List<MediaContainerModel> gallery = new ArrayList<>();
		for (int position = 0; position < 4; position++)
		{
			final MediaContainerModel container = findContainer(product.getCode() + PHOTO_CONTAINER_SUFFIX + position, catalogVersion);
			if (container != null)
			{
				gallery.add(container);
			}
		}
		for (final MediaContainerModel other : safe(product.getGalleryImages()))
		{
			if (!gallery.contains(other))
			{
				gallery.add(other);
			}
		}
		product.setGalleryImages(gallery);
		modelService.save(product);
		refreshSearch(product);
		LOG.info("{} foto(s) do cadastro gravada(s) no produto {} ({})", count, product.getCode(), catalogVersion.getVersion());
		return count + (count == 1 ? " foto" : " fotos") + " no produto " + product.getCode() + ".";
	}

	@Override
	public MediaModel productPhoto(final ProductModel product, final int position)
	{
		if (product == null || product.getCatalogVersion() == null || product.getPk() == null)
		{
			return null;
		}
		if (position == 0 && product.getPicture() != null)
		{
			return product.getPicture();
		}
		final List<MediaContainerModel> gallery = new ArrayList<>(safe(product.getGalleryImages()));
		if (position >= gallery.size())
		{
			return null;
		}
		final String pictureFormat = config("flamengo.produtos.imagem.formato.principal", "300Wx300H");
		MediaModel first = null;
		for (final MediaModel media : safe(gallery.get(position).getMedias()))
		{
			if (media.getMediaFormat() != null && pictureFormat.equals(media.getMediaFormat().getQualifier()))
			{
				return media;
			}
			first = first == null ? media : first;
		}
		return first;
	}

	/** Um conjunto de mídias com a foto em cada tamanho da loja; devolve formato → mídia */
	protected Map<String, MediaModel> fillContainer(final MediaModel image, final ProductModel product, final String containerCode)
	{
		final CatalogVersionModel catalogVersion = product.getCatalogVersion();
		final byte[] original = mediaService.getDataFromMedia(image);
		final BufferedImage source = read(original);
		MediaContainerModel container = findContainer(containerCode, catalogVersion);
		if (container == null)
		{
			container = modelService.create(MediaContainerModel.class);
			container.setQualifier(containerCode);
			container.setCatalogVersion(catalogVersion);
			modelService.save(container);
		}
		final MediaFolderModel folder = folder();
		final boolean png = "image/png".equalsIgnoreCase(image.getMime());
		final Map<String, MediaModel> byFormat = new java.util.HashMap<>();
		for (final String qualifier : formats())
		{
			final MediaFormatModel format = format(qualifier);
			if (format == null)
			{
				continue;
			}
			final String code = containerCode + "-" + qualifier;
			MediaModel media = findMedia(code, catalogVersion);
			if (media == null)
			{
				media = modelService.create(MediaModel.class);
				media.setCode(code);
				media.setCatalogVersion(catalogVersion);
			}
			final byte[] data = source == null ? original : resize(source, size(qualifier), png, quality());
			media.setMediaFormat(format);
			media.setMediaContainer(container);
			media.setMime(source == null ? image.getMime() : png ? "image/png" : "image/jpeg");
			media.setRealFileName(code + (source == null ? extension(image.getMime()) : png ? ".png" : ".jpg"));
			media.setAltText(product.getName(new Locale("pt")));
			if (folder != null)
			{
				media.setFolder(folder);
			}
			modelService.save(media);
			mediaService.setDataForMedia(media, data);
			byFormat.put(qualifier, media);
		}
		modelService.refresh(container);
		return byFormat;
	}

	/** Foto nova ou retirada: a busca (vitrine da home) passa a ter o endereço novo em segundos */
	protected void refreshSearch(final ProductModel product)
	{
		if (searchIndexRefresher != null)
		{
			searchIndexRefresher.refreshLater(product);
		}
	}

	@Override
	public String url(final MediaModel image)
	{
		return image == null ? null : image.getURL();
	}

	// ------------------------------------------------------------ imagem

	protected static BufferedImage read(final byte[] data)
	{
		if (data == null || data.length == 0)
		{
			return null;
		}
		try (InputStream in = new ByteArrayInputStream(data))
		{
			return ImageIO.read(in);
		}
		catch (final IOException | RuntimeException e)
		{
			return null;
		}
	}

	/**
	 * Tamanho máximo gravado num formato (07/11, fotos nítidas): {@code flamengo.produtos.imagem.tamanho.<formato>}
	 * (ex.: {@code flamengo.produtos.imagem.tamanho.515Wx515H=1600Wx1600H}) grava a foto maior que o nome do
	 * formato, para a foto grande e a ampliada da loja ficarem nítidas. Sem a chave, o tamanho do próprio formato.
	 */
	protected String size(final String qualifier)
	{
		return config("flamengo.produtos.imagem.tamanho." + qualifier, qualifier);
	}

	/** Qualidade do JPG (0 a 1); chave flamengo.produtos.imagem.qualidade, padrão 0.9 */
	protected float quality()
	{
		try
		{
			final float q = Float.parseFloat(config("flamengo.produtos.imagem.qualidade", "0.9"));
			return q > 0f && q <= 1f ? q : 0.9f;
		}
		catch (final NumberFormatException e)
		{
			return 0.9f;
		}
	}

	/** Reduz para caber no tamanho (ex.: 515Wx515H), sem cortar nem aumentar */
	protected static byte[] resize(final BufferedImage source, final String qualifier, final boolean png)
	{
		return resize(source, qualifier, png, 0.9f);
	}

	/**
	 * Reduz para caber no tamanho (ex.: 515Wx515H), sem cortar nem aumentar. Redução grande é feita em
	 * etapas (metade por vez), que deixa a foto mais nítida que uma redução direta; JPG na qualidade dada.
	 */
	protected static byte[] resize(final BufferedImage source, final String qualifier, final boolean png, final float quality)
	{
		int maxWidth = source.getWidth();
		int maxHeight = source.getHeight();
		final Matcher m = SIZE.matcher(qualifier);
		if (m.find())
		{
			maxWidth = Integer.parseInt(m.group(1));
			maxHeight = Integer.parseInt(m.group(2));
		}
		final double scale = Math.min(1d, Math.min((double) maxWidth / source.getWidth(), (double) maxHeight / source.getHeight()));
		final int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
		final int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
		BufferedImage current = source;
		int w = source.getWidth();
		int h = source.getHeight();
		do
		{
			w = Math.max(width, w / 2);
			h = Math.max(height, h / 2);
			if (w < width * 2 && h < height * 2)
			{
				w = width;
				h = height;
			}
			current = draw(current, w, h, png);
		}
		while (w != width || h != height);
		try (ByteArrayOutputStream out = new ByteArrayOutputStream())
		{
			if (png)
			{
				ImageIO.write(current, "png", out);
			}
			else
			{
				writeJpg(current, out, quality);
			}
			return out.toByteArray();
		}
		catch (final IOException e)
		{
			throw new IllegalStateException("Não foi possível reduzir a imagem para " + qualifier + ".", e);
		}
	}

	protected static BufferedImage draw(final BufferedImage from, final int width, final int height, final boolean png)
	{
		final BufferedImage target = new BufferedImage(width, height, png ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
		final Graphics2D g = target.createGraphics();
		try
		{
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			if (!png)
			{
				g.setColor(Color.WHITE);
				g.fillRect(0, 0, width, height);
			}
			g.drawImage(from, 0, 0, width, height, null);
		}
		finally
		{
			g.dispose();
		}
		return target;
	}

	protected static void writeJpg(final BufferedImage image, final ByteArrayOutputStream out, final float quality) throws IOException
	{
		final java.util.Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
		if (!writers.hasNext())
		{
			ImageIO.write(image, "jpg", out);
			return;
		}
		final ImageWriter writer = writers.next();
		try (ImageOutputStream ios = ImageIO.createImageOutputStream(out))
		{
			writer.setOutput(ios);
			final ImageWriteParam param = writer.getDefaultWriteParam();
			param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
			param.setCompressionQuality(quality);
			writer.write(null, new IIOImage(image, null, null), param);
		}
		finally
		{
			writer.dispose();
		}
	}

	protected static String extension(final String mime)
	{
		if ("image/png".equalsIgnoreCase(mime))
		{
			return ".png";
		}
		if ("image/gif".equalsIgnoreCase(mime))
		{
			return ".gif";
		}
		return ".jpg";
	}

	// ------------------------------------------------------------ busca

	protected List<String> formats()
	{
		final List<String> out = new ArrayList<>();
		for (final String f : config("flamengo.produtos.imagem.formatos", "515Wx515H,300Wx300H,96Wx96H,65Wx65H").split(","))
		{
			if (StringUtils.isNotBlank(f))
			{
				out.add(f.trim());
			}
		}
		return out;
	}

	protected MediaFormatModel format(final String qualifier)
	{
		try
		{
			return mediaService.getFormat(qualifier);
		}
		catch (final RuntimeException e)
		{
			LOG.warn("Formato de mídia {} não existe; pulando", qualifier);
			return null;
		}
	}

	protected MediaFolderModel folder()
	{
		try
		{
			return mediaService.getFolder(config("flamengo.produtos.imagem.pasta", "images"));
		}
		catch (final RuntimeException e)
		{
			return null;
		}
	}

	protected MediaContainerModel findContainer(final String qualifier, final CatalogVersionModel catalogVersion)
	{
		final FlexibleSearchQuery query = new FlexibleSearchQuery(
				"SELECT {pk} FROM {MediaContainer} WHERE {qualifier}=?q AND {catalogVersion}=?cv");
		query.addQueryParameter("q", qualifier);
		query.addQueryParameter("cv", catalogVersion);
		final List<MediaContainerModel> result = flexibleSearchService.<MediaContainerModel> search(query).getResult();
		return result.isEmpty() ? null : result.get(0);
	}

	protected MediaModel findMedia(final String code, final CatalogVersionModel catalogVersion)
	{
		final FlexibleSearchQuery query = new FlexibleSearchQuery(
				"SELECT {pk} FROM {Media} WHERE {code}=?c AND {catalogVersion}=?cv");
		query.addQueryParameter("c", code);
		query.addQueryParameter("cv", catalogVersion);
		final List<MediaModel> result = flexibleSearchService.<MediaModel> search(query).getResult();
		return result.isEmpty() ? null : result.get(0);
	}

	protected String config(final String key, final String fallback)
	{
		return StringUtils.defaultIfBlank(configurationService.getConfiguration().getString(key), fallback);
	}

	protected static <T> Collection<T> safe(final Collection<T> values)
	{
		return values == null ? Collections.emptyList() : values;
	}

	public void setConfigurationService(final ConfigurationService configurationService)
	{
		this.configurationService = configurationService;
	}

	public void setMediaService(final MediaService mediaService)
	{
		this.mediaService = mediaService;
	}

	public void setModelService(final ModelService modelService)
	{
		this.modelService = modelService;
	}

	public void setSearchIndexRefresher(final ProductSearchIndexRefresher searchIndexRefresher)
	{
		this.searchIndexRefresher = searchIndexRefresher;
	}

	public void setFlexibleSearchService(final FlexibleSearchService flexibleSearchService)
	{
		this.flexibleSearchService = flexibleSearchService;
	}
}
