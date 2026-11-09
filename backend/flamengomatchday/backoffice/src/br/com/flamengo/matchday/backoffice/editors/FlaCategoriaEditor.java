package br.com.flamengo.matchday.backoffice.editors;

import br.com.flamengo.matchday.loja.ProdutoCadastroService;
import de.hybris.platform.category.model.CategoryModel;
import de.hybris.platform.core.Registry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zkoss.util.resource.Labels;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.event.Events;
import org.zkoss.zk.ui.event.InputEvent;
import org.zkoss.zul.Combobox;
import org.zkoss.zul.Comboitem;
import org.zkoss.zul.Div;
import org.zkoss.zul.Html;
import org.zkoss.zul.Label;
import org.zkoss.zul.Textbox;

import com.hybris.cockpitng.editors.EditorContext;
import com.hybris.cockpitng.editors.EditorListener;
import com.hybris.cockpitng.editors.impl.AbstractCockpitEditorRenderer;


/**
 * Editor "br.com.flamengo.matchday.editor.categoria" (08/11): escolha da categoria da loja pelo caminho inteiro
 * ("Mantos › Jogo 1 › Masculino"), com o código ao lado. Lista só as categorias do catálogo da loja, sem as técnicas
 * (tamanho, ingressos, sócio...). Digitar filtra por qualquer parte do caminho ou do código.
 */
public class FlaCategoriaEditor extends AbstractCockpitEditorRenderer<Object>
{
	private static final Logger LOG = LoggerFactory.getLogger(FlaCategoriaEditor.class);
	private static final int MAX_ITENS = 60;

	@Override
	public void render(final Component parent, final EditorContext<Object> context, final EditorListener<Object> listener)
	{
		final ProdutoCadastroService service = service();
		final List<Opcao> opcoes = new ArrayList<>();
		try
		{
			for (final CategoryModel category : service.categoriasDaLoja())
			{
				opcoes.add(new Opcao(category, service.caminho(category)));
			}
		}
		catch (final RuntimeException e)
		{
			LOG.warn("Categorias da loja indisponíveis: {}", e.getMessage(), e);
		}

		if ("lista".equals(String.valueOf(context.getParameter("modo"))))
		{
			renderLista(parent, context, listener, service, opcoes);
			return;
		}

		final Div box = new Div();
		box.setStyle("display:flex;flex-direction:column;gap:6px;width:100%;max-width:560px");
		final Combobox combo = new Combobox();
		combo.setWidth("100%");
		combo.setAutodrop(true);
		combo.setButtonVisible(true);
		combo.setPlaceholder(text("fla.categoria.placeholder", "Digite parte do nome, ex.: jogo 1"));
		combo.setDisabled(!context.isEditable());
		final Label dica = new Label();
		dica.setStyle("font-size:12px;color:#556b82");

		final Object initial = context.getInitialValue();
		if (initial instanceof CategoryModel)
		{
			combo.setValue(service.caminho((CategoryModel) initial));
			dica.setValue(dica((CategoryModel) initial, service));
		}
		preencher(combo, opcoes, "");

		combo.addEventListener(Events.ON_CHANGING, event -> preencher(combo, opcoes, ((InputEvent) event).getValue()));
		combo.addEventListener(Events.ON_SELECT, event -> {
			final Comboitem item = combo.getSelectedItem();
			final Object value = item == null ? null : item.getValue();
			if (value instanceof CategoryModel)
			{
				dica.setValue(dica((CategoryModel) value, service));
				listener.onValueChanged(value);
			}
		});
		box.appendChild(combo);
		box.appendChild(dica);
		parent.appendChild(box);
	}

	/**
	 * Modo "lista" (assistente, 08/11, mockup): busca em cima, lista com o caminho inteiro (último nível em negrito) e o
	 * código, uma linha marcada, e o aviso de onde o produto aparece na loja.
	 */
	protected void renderLista(final Component parent, final EditorContext<Object> context, final EditorListener<Object> listener,
			final ProdutoCadastroService service, final List<Opcao> opcoes)
	{
		final Div box = new Div();
		box.setSclass("fla-cat-box");
		box.setStyle("display:flex;flex-direction:column;gap:14px;width:100%");
		FlaCadastroEstilo.aplicar(box);

		final Textbox busca = new Textbox();
		busca.setSclass("fla-cad-busca");
		busca.setPlaceholder(text("fla.categoria.placeholder", "Digite parte do nome, ex.: jogo 1"));
		busca.setDisabled(!context.isEditable());
		final Div lista = new Div();
		lista.setSclass("fla-cat-lista");
		final Div aviso = new Div();
		aviso.setSclass("fla-cad-aviso");

		final CategoryModel[] atual = { context.getInitialValue() instanceof CategoryModel ? (CategoryModel) context.getInitialValue()
				: null };
		final String[] filtro = { "" };
		final Runnable[] montar = new Runnable[1];
		montar[0] = () -> {
			lista.getChildren().clear();
			final String f = StringUtils.stripAccents(StringUtils.defaultString(filtro[0])).toLowerCase(Locale.ROOT).trim();
			int count = 0;
			for (final Opcao opcao : opcoes)
			{
				if (!f.isEmpty() && !opcao.busca.contains(f))
				{
					continue;
				}
				lista.appendChild(linha(opcao, atual[0] != null && atual[0].equals(opcao.categoria), context.isEditable(), () -> {
					atual[0] = opcao.categoria;
					listener.onValueChanged(opcao.categoria);
					montar[0].run();
				}));
				if (++count >= MAX_ITENS)
				{
					break;
				}
			}
			if (count == 0)
			{
				final Label nada = new Label(text("fla.categoria.nada", "Nenhuma categoria com esse nome."));
				nada.setSclass("fla-cat-vazio");
				lista.appendChild(nada);
			}
			aviso.getChildren().clear();
			aviso.appendChild(new Html(FlaCadastroEstilo.ICONE_INFO));
			aviso.appendChild(new Html("<span>" + avisoHtml(atual[0], service) + "</span>"));
		};
		busca.addEventListener(Events.ON_CHANGING, event -> {
			filtro[0] = ((InputEvent) event).getValue();
			montar[0].run();
		});
		montar[0].run();

		box.appendChild(busca);
		box.appendChild(lista);
		box.appendChild(aviso);
		parent.appendChild(box);
	}

	protected static Div linha(final Opcao opcao, final boolean selecionada, final boolean editavel, final Runnable escolher)
	{
		final Div linha = new Div();
		linha.setSclass(selecionada ? "fla-cat-linha fla-cat-sel" : "fla-cat-linha");
		final Div radio = new Div();
		radio.setSclass("fla-cat-radio");
		linha.appendChild(radio);
		final String[] partes = opcao.caminho.split(" › ");
		final StringBuilder html = new StringBuilder("<span>");
		for (int i = 0; i < partes.length; i++)
		{
			if (i > 0)
			{
				html.append(" <span class=\"fla-cat-sep\">›</span> ");
			}
			final String parte = escape(partes[i]);
			html.append(i == partes.length - 1 ? "<strong>" + parte + "</strong>" : parte);
		}
		html.append("</span>");
		linha.appendChild(new Html(html.toString()));
		final Label codigo = new Label(opcao.categoria.getCode());
		codigo.setSclass("fla-cat-cod");
		linha.appendChild(codigo);
		if (editavel)
		{
			linha.addEventListener(Events.ON_CLICK, event -> escolher.run());
		}
		return linha;
	}

	/** "A lista mostra só as categorias do catálogo do Flamengo... Na loja, o produto aparece em Masculino e também em..." */
	protected static String avisoHtml(final CategoryModel category, final ProdutoCadastroService service)
	{
		final StringBuilder out = new StringBuilder(escape(text("fla.categoria.aviso",
				"A lista mostra só as categorias do catálogo do Flamengo, com o caminho inteiro.")));
		if (category == null)
		{
			return out.toString();
		}
		final List<String> partes = new ArrayList<>(List.of(service.caminho(category).split(" › ")));
		final String ultima = partes.remove(partes.size() - 1);
		out.append(' ').append(escape(text("fla.categoria.aparece", "Na loja, o produto aparece em"))).append(" <strong>")
				.append(escape(ultima)).append("</strong>");
		if (!partes.isEmpty())
		{
			Collections.reverse(partes);
			out.append(' ').append(escape(text("fla.categoria.etambem", "e também em"))).append(' ');
			for (int i = 0; i < partes.size(); i++)
			{
				if (i > 0)
				{
					out.append(i == partes.size() - 1 ? " " + escape(text("fla.categoria.e", "e")) + " " : ", ");
				}
				out.append("<strong>").append(escape(partes.get(i))).append("</strong>");
			}
		}
		return out.append('.').toString();
	}

	protected static String escape(final String value)
	{
		return StringUtils.defaultString(value).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
	}

	protected static void preencher(final Combobox combo, final List<Opcao> opcoes, final String filtro)
	{
		combo.getItems().clear();
		final String f = StringUtils.stripAccents(StringUtils.defaultString(filtro)).toLowerCase(Locale.ROOT).trim();
		int count = 0;
		for (final Opcao opcao : opcoes)
		{
			if (!f.isEmpty() && !opcao.busca.contains(f))
			{
				continue;
			}
			final Comboitem item = new Comboitem(opcao.caminho);
			item.setDescription(opcao.categoria.getCode());
			item.setValue(opcao.categoria);
			combo.appendChild(item);
			if (++count >= MAX_ITENS)
			{
				break;
			}
		}
	}

	/** "Na loja, aparece também em Jogo 1 e Mantos." */
	protected static String dica(final CategoryModel category, final ProdutoCadastroService service)
	{
		final String caminho = service.caminho(category);
		final List<String> partes = new ArrayList<>(List.of(caminho.split(" › ")));
		if (partes.size() < 2)
		{
			return text("fla.categoria.codigo", "Código") + ": " + category.getCode();
		}
		partes.remove(partes.size() - 1);
		Collections.reverse(partes);
		final String lista = partes.size() == 1 ? partes.get(0)
				: String.join(", ", partes.subList(0, partes.size() - 1)) + " e " + partes.get(partes.size() - 1);
		return category.getCode() + " · " + text("fla.categoria.tambem", "Na loja, aparece também em") + " " + lista + ".";
	}

	protected static ProdutoCadastroService service()
	{
		return Registry.getApplicationContext().getBean("produtoCadastroService", ProdutoCadastroService.class);
	}

	protected static String text(final String key, final String fallback)
	{
		final String value = Labels.getLabel(key);
		return StringUtils.isBlank(value) ? fallback : value;
	}

	protected static final class Opcao
	{
		final CategoryModel categoria;
		final String caminho;
		final String busca;

		Opcao(final CategoryModel categoria, final String caminho)
		{
			this.categoria = categoria;
			this.caminho = caminho;
			this.busca = StringUtils.stripAccents(caminho + " " + categoria.getCode()).toLowerCase(Locale.ROOT);
		}
	}
}
