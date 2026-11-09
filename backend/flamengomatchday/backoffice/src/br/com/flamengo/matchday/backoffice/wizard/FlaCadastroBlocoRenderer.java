package br.com.flamengo.matchday.backoffice.wizard;

import br.com.flamengo.matchday.backoffice.editors.FlaCadastroEstilo;
import de.hybris.platform.catalog.CatalogVersionService;
import de.hybris.platform.catalog.model.CatalogVersionModel;
import de.hybris.platform.core.Registry;
import de.hybris.platform.core.model.c2l.CurrencyModel;
import de.hybris.platform.servicelayer.config.ConfigurationService;
import de.hybris.platform.servicelayer.i18n.CommonI18NService;

import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zkoss.util.resource.Labels;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.util.Clients;
import org.zkoss.zul.Div;
import org.zkoss.zul.Html;
import org.zkoss.zul.Label;

import com.hybris.cockpitng.config.jaxb.wizard.ViewType;
import com.hybris.cockpitng.dataaccess.facades.type.DataType;
import com.hybris.cockpitng.engine.WidgetInstanceManager;
import com.hybris.cockpitng.widgets.configurableflow.renderer.DefaultCustomViewRenderer;


/**
 * Blocos próprios dos passos do assistente "Novo produto" (08/11, mockup aprovado), entre os campos padrão do passo
 * ({@code <wz:custom-view lang="zul"><wz:renderer spring-bean="flaCadastroBlocoRenderer">}). Parâmetros:
 * <ul>
 * <li>{@code passo}: número do passo; marca o conteúdo ({@code fla-cad-passoN}) e aplica o visual na janela;</li>
 * <li>{@code tipo}: {@code campo} (rótulo + valor só para leitura), {@code dica} (texto pequeno embaixo do campo anterior),
 * {@code aviso} (caixa azul de largura inteira), {@code vazio} (só ocupa a célula) ou {@code marca} (nada na tela);</li>
 * <li>{@code rotulo} / {@code texto}: chaves de rótulo;</li>
 * <li>{@code valor}: {@code catalogo}, {@code moeda} ou {@code deposito} (lidos da configuração do cadastro).</li>
 * </ul>
 * Não grava nada: os campos editáveis continuam sendo os do Backoffice, com as mesmas regras.
 */
public class FlaCadastroBlocoRenderer extends DefaultCustomViewRenderer
{
	private static final Logger LOG = LoggerFactory.getLogger(FlaCadastroBlocoRenderer.class);

	/** Tira os dois-pontos que o assistente põe no fim de cada rótulo (o mockup não tem) */
	private static final String SEM_DOIS_PONTOS = "setTimeout(function(){document.querySelectorAll("
			+ "'.fla-cad-janela .fla-cad-conteudo .yw-wizard-property-label').forEach(function(l){"
			+ "var t=l.textContent;if(/:\\s*$/.test(t)){l.textContent=t.replace(/:\\s*$/,'');}});},60);";

	@Override
	public void render(final Component parent, final ViewType customView, final Map<String, String> parameters,
			final DataType dataType, final WidgetInstanceManager widgetInstanceManager)
	{
		final String passo = param(parameters, "passo");
		if (StringUtils.isNotBlank(passo))
		{
			FlaCadastroEstilo.marcar(parent, "fla-cad-conteudo", "fla-cad-passo" + passo);
		}
		FlaCadastroEstilo.aplicar(parent);

		final String tipo = StringUtils.defaultIfBlank(param(parameters, "tipo"), "vazio");
		switch (tipo)
		{
			case "campo":
				parent.appendChild(campo(text(param(parameters, "rotulo"), ""), valor(param(parameters, "valor"))));
				break;
			case "dica":
				parent.appendChild(dica(text(param(parameters, "texto"), "")));
				break;
			case "aviso":
				parent.appendChild(aviso(text(param(parameters, "texto"), "")));
				break;
			case "marca":
				// só marca o passo e aplica o visual
				break;
			default:
				parent.appendChild(new Div());
				break;
		}
		Clients.evalJavaScript(SEM_DOIS_PONTOS);
	}

	protected static Component campo(final String rotulo, final String valor)
	{
		final Div box = new Div();
		final Label label = new Label(rotulo);
		label.setSclass("fla-cad-rotulo");
		final Div value = new Div();
		value.setSclass("fla-cad-ro");
		value.appendChild(new Label(valor));
		value.setTooltiptext(valor);
		box.appendChild(label);
		box.appendChild(value);
		return box;
	}

	protected static Component dica(final String texto)
	{
		final Div box = new Div();
		box.setSclass("fla-cad-dica");
		box.appendChild(new Label(texto));
		return box;
	}

	public static Component aviso(final String texto)
	{
		final Div box = new Div();
		box.setSclass("fla-cad-aviso");
		box.appendChild(new Html(FlaCadastroEstilo.ICONE_INFO));
		final Label label = new Label(texto);
		box.appendChild(label);
		return box;
	}

	/** Valor só para leitura a partir da configuração do cadastro (flamengo.loja.cadastro.*) */
	protected static String valor(final String qual)
	{
		final String catalogo = config("flamengo.loja.cadastro.catalogo", "flamengoProductCatalog");
		final String versao = config("flamengo.loja.cadastro.versao", "Online");
		try
		{
			switch (StringUtils.defaultString(qual))
			{
				case "catalogo":
					final CatalogVersionModel cv = Registry.getApplicationContext()
							.getBean("catalogVersionService", CatalogVersionService.class).getCatalogVersion(catalogo, versao);
					return StringUtils.defaultIfBlank(cv.getCatalog().getName(), catalogo) + " : " + cv.getVersion();
				case "moeda":
					final String iso = config("flamengo.loja.cadastro.moeda", "BRL");
					final CurrencyModel currency = Registry.getApplicationContext()
							.getBean("commonI18NService", CommonI18NService.class).getCurrency(iso);
					return StringUtils.isBlank(currency.getName()) ? iso : currency.getName() + " (" + iso + ")";
				case "deposito":
					return config("flamengo.loja.cadastro.deposito", "warehouse_s");
				default:
					return StringUtils.defaultString(qual);
			}
		}
		catch (final RuntimeException e)
		{
			LOG.debug("Valor '{}' do cadastro sem nome: {}", qual, e.getMessage());
			return "catalogo".equals(qual) ? catalogo + " : " + versao
					: "moeda".equals(qual) ? config("flamengo.loja.cadastro.moeda", "BRL") : StringUtils.defaultString(qual);
		}
	}

	protected static String config(final String key, final String fallback)
	{
		return StringUtils.defaultIfBlank(Registry.getApplicationContext().getBean("configurationService", ConfigurationService.class)
				.getConfiguration().getString(key), fallback);
	}

	protected static String param(final Map<String, String> parameters, final String key)
	{
		return parameters == null ? null : parameters.get(key);
	}

	protected static String text(final String key, final String fallback)
	{
		if (StringUtils.isBlank(key))
		{
			return fallback;
		}
		final String value = Labels.getLabel(key);
		return StringUtils.isBlank(value) ? fallback : value;
	}
}
