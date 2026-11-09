package br.com.flamengo.matchday.backoffice.editors;

import br.com.flamengo.matchday.media.ProductImageService;
import de.hybris.platform.core.Registry;
import de.hybris.platform.core.model.media.MediaModel;
import de.hybris.platform.servicelayer.media.MediaService;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zkoss.image.AImage;
import org.zkoss.util.media.Media;
import org.zkoss.util.resource.Labels;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.event.Events;
import org.zkoss.zk.ui.event.UploadEvent;
import org.zkoss.zk.ui.util.Clients;
import org.zkoss.zul.Button;
import org.zkoss.zul.Div;
import org.zkoss.zul.Image;
import org.zkoss.zul.Label;

import com.hybris.cockpitng.editors.EditorContext;
import com.hybris.cockpitng.editors.EditorListener;
import com.hybris.cockpitng.editors.impl.AbstractCockpitEditorRenderer;


/**
 * Editor "br.com.flamengo.matchday.editor.image" (04/11): imagem do jogo ou da experiência com
 * pré-visualização e envio direto do arquivo, sem passar pelo cadastro de mídias. A vitrine de
 * experiências usa o mesmo bloco por {@link #build}.
 */
public class FlaImageEditor extends AbstractCockpitEditorRenderer<Object>
{
	private static final Logger LOG = LoggerFactory.getLogger(FlaImageEditor.class);
	/** Parâmetro "remove" do editor (false esconde o botão Remover), passado ao bloco durante a montagem */
	private static final ThreadLocal<String> REMOVABLE = new ThreadLocal<>();

	@Override
	public void render(final Component parent, final EditorContext<Object> context, final EditorListener<Object> listener)
	{
		final Object initial = context.getInitialValue();
		final Object prefix = context.getParameter("prefix");
		final Object ratio = context.getParameter("ratio");
		final Object hint = context.getParameter("hint");
		final Object removable = context.getParameter("remove");
		REMOVABLE.set(removable == null ? null : removable.toString());
		try
		{
			build(parent, initial instanceof MediaModel ? (MediaModel) initial : null, context.isEditable(),
					prefix == null ? "imagem" : prefix.toString(), ratio == null ? "16/9" : ratio.toString(),
					hint == null ? null : hint.toString(), media -> listener.onValueChanged(media));
		}
		finally
		{
			REMOVABLE.remove();
		}
	}

	/**
	 * Monta o bloco: moldura 16:9 com a imagem (ou "Sem imagem"), tamanho, e os botões Enviar/Trocar e
	 * Remover. {@code onChange} recebe a mídia nova (ou null ao remover).
	 */
	public static Div build(final Component parent, final MediaModel initial, final boolean editable, final String prefix,
			final Consumer<MediaModel> onChange)
	{
		return build(parent, initial, editable, prefix, "16/9", null, onChange);
	}

	/**
	 * Igual ao bloco padrão, com a proporção da moldura (ex.: "1/1" para foto de produto) e a chave do rótulo da dica
	 * (parâmetros "ratio" e "hint" do editor, 08/11). Sem eles, 16:9 e a dica de jogo/experiência.
	 */
	public static Div build(final Component parent, final MediaModel initial, final boolean editable, final String prefix,
			final String ratio, final String hintKey, final Consumer<MediaModel> onChange)
	{
		final boolean square = "1/1".equals(ratio);
		final String[] removable = { REMOVABLE.get() };
		final Div box = new Div();
		box.setSclass("fla-image-editor");
		if (square)
		{
			// foto de produto (assistente e aba "Cadastro"): visual do cadastro (08/11)
			FlaCadastroEstilo.aplicar(box);
		}
		box.setStyle("display:flex;flex-direction:column;gap:6px;max-width:360px");
		final Div frame = new Div();
		frame.setStyle("width:" + (square ? "200px" : "320px") + ";max-width:100%;aspect-ratio:" + (ratio == null ? "16/9" : ratio)
				+ ";border:1px solid #d9dde3;border-radius:8px;background:#f5f6f7;"
				+ "display:flex;align-items:center;justify-content:center;overflow:hidden");
		final Label info = new Label();
		info.setStyle("font-size:11px;color:#556b82");
		box.appendChild(frame);
		box.appendChild(info);

		final Button upload = new Button();
		final Button remove = new Button(text("fla.image.remove", "Remover"));
		final Runnable[] refresh = new Runnable[1];
		final MediaModel[] current = { initial };
		refresh[0] = () -> {
			frame.getChildren().clear();
			final MediaModel media = current[0];
			final AImage preview = preview(media);
			if (preview != null)
			{
				final Image image = new Image();
				image.setContent(preview);
				image.setStyle("width:100%;height:100%;object-fit:" + (square ? "contain" : "cover"));
				frame.appendChild(image);
			}
			else
			{
				final Label none = new Label(media == null ? text("fla.image.none", "Sem imagem") : StringUtils.defaultString(media.getCode()));
				none.setStyle("color:#6a7d8f;font-size:12px");
				frame.appendChild(none);
			}
			info.setValue(media == null ? "" : StringUtils.defaultString(media.getRealFileName(), media.getCode())
					+ (StringUtils.isBlank(media.getAltText()) ? "" : " · " + media.getAltText()));
			upload.setLabel(media == null ? text("fla.image.upload", "Enviar imagem") : text("fla.image.change", "Trocar imagem"));
			remove.setVisible(editable && media != null && !"false".equals(removable[0]));
		};

		if (editable)
		{
			final Div buttons = new Div();
			buttons.setStyle("display:flex;gap:8px;flex-wrap:wrap");
			upload.setUpload("true,maxsize=5120,accept=image/jpeg|image/png|image/gif");
			upload.addEventListener(Events.ON_UPLOAD, event -> {
				final Media file = ((UploadEvent) event).getMedia();
				if (file == null)
				{
					return;
				}
				try
				{
					final MediaModel media = imageService().createUpload(prefix, file.getName(), file.getContentType(), bytes(file));
					current[0] = media;
					refresh[0].run();
					onChange.accept(media);
				}
				catch (final IllegalArgumentException e)
				{
					Clients.showNotification(e.getMessage(), "warning", box, "end_center", 6000);
				}
				catch (final RuntimeException | IOException e)
				{
					LOG.warn("Imagem não enviada: {}", e.getMessage(), e);
					Clients.showNotification(text("fla.image.error", "Não foi possível enviar a imagem.") + " " + e.getMessage(), "error",
							box, "end_center", 8000);
				}
			});
			remove.addEventListener(Events.ON_CLICK, event -> {
				current[0] = null;
				refresh[0].run();
				onChange.accept(null);
			});
			buttons.appendChild(upload);
			buttons.appendChild(remove);
			box.appendChild(buttons);
			final Label hint = new Label(hintKey != null ? text(hintKey, "JPG ou PNG, até 5 MB.")
					: text("fla.image.hint", "Foto horizontal, 1200 x 675 px (16:9), JPG ou PNG, até 5 MB."));
			hint.setStyle("font-size:11px;color:#6a7d8f");
			hint.setSclass("fla-image-dica");
			box.appendChild(hint);
		}
		refresh[0].run();
		parent.appendChild(box);
		return box;
	}

	protected static AImage preview(final MediaModel media)
	{
		if (media == null)
		{
			return null;
		}
		try
		{
			final MediaService mediaService = Registry.getApplicationContext().getBean("mediaService", MediaService.class);
			return new AImage(StringUtils.defaultIfBlank(media.getRealFileName(), media.getCode()), mediaService.getDataFromMedia(media));
		}
		catch (final RuntimeException | IOException e)
		{
			LOG.debug("Sem pré-visualização da mídia {}: {}", media.getCode(), e.getMessage());
			return null;
		}
	}

	protected static byte[] bytes(final Media file) throws IOException
	{
		if (!file.isBinary())
		{
			return file.getStringData().getBytes(StandardCharsets.UTF_8);
		}
		if (file.inMemory())
		{
			return file.getByteData();
		}
		try (InputStream in = file.getStreamData())
		{
			return IOUtils.toByteArray(in);
		}
	}

	protected static ProductImageService imageService()
	{
		return Registry.getApplicationContext().getBean("flamengoProductImageService", ProductImageService.class);
	}

	protected static String text(final String key, final String fallback)
	{
		final String value = Labels.getLabel(key);
		return StringUtils.isBlank(value) ? fallback : value;
	}
}
