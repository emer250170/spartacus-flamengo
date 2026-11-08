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
import org.zkoss.zul.Label;

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
