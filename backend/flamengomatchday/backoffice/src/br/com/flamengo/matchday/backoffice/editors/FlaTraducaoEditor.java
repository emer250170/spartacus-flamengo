package br.com.flamengo.matchday.backoffice.editors;

import br.com.flamengo.matchday.ia.TraducaoService;
import de.hybris.platform.core.Registry;
import de.hybris.platform.core.model.product.ProductModel;

import java.lang.reflect.Method;
import java.util.Locale;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zkoss.util.resource.Labels;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.event.Events;
import org.zkoss.zk.ui.util.Clients;
import org.zkoss.zul.Button;
import org.zkoss.zul.Div;
import org.zkoss.zul.Label;
import org.zkoss.zul.Textbox;

import com.hybris.cockpitng.editors.EditorContext;
import com.hybris.cockpitng.editors.EditorListener;
import com.hybris.cockpitng.editors.impl.AbstractCockpitEditorRenderer;


/**
 * Editor "br.com.flamengo.matchday.editor.traducao" (08/11): campo de texto em inglês com o botão "Traduzir com IA", que
 * traduz o mesmo campo em português do produto (parâmetro "source": nome ou resumo) e põe o resultado no campo para o
 * gestor revisar. Nada é gravado até salvar. Sem a tradução disponível (desligada ou sem chave), é um campo comum.
 * Parâmetro "rows" (opcional) deixa o campo com várias linhas.
 */
public class FlaTraducaoEditor extends AbstractCockpitEditorRenderer<Object>
{
	private static final Logger LOG = LoggerFactory.getLogger(FlaTraducaoEditor.class);
	private static final Locale PT = new Locale("pt");

	@Override
	public void render(final Component parent, final EditorContext<Object> context, final EditorListener<Object> listener)
	{
		final Object rowsParam = context.getParameter("rows");
		final int rows = rowsParam == null ? 1 : Math.max(1, Integer.parseInt(rowsParam.toString()));
		final String source = StringUtils.defaultIfBlank(context.getParameter("source") == null ? null
				: context.getParameter("source").toString(), "nome");

		final Div box = new Div();
		box.setSclass("fla-traducao-box");
		box.setStyle("display:flex;flex-direction:column;gap:4px;width:100%");
		FlaCadastroEstilo.aplicar(box);
		final Textbox textbox = new Textbox();
		textbox.setWidth("100%");
		if (rows > 1)
		{
			textbox.setMultiline(true);
			textbox.setRows(rows);
		}
		final Object initial = context.getInitialValue();
		textbox.setValue(initial == null ? "" : initial.toString());
		textbox.setReadonly(!context.isEditable());
		textbox.addEventListener(Events.ON_CHANGE, event -> listener.onValueChanged(StringUtils.trimToNull(textbox.getValue())));

		final TraducaoService traducao = service();
		if (context.isEditable() && traducao != null && traducao.disponivel())
		{
			final Button button = new Button(text("fla.traducao.botao", "Traduzir com IA"));
			// link discreto acima do campo (08/11, mockup); no assistente fica na linha do rótulo
			button.setSclass("fla-traducao-botao fla-ia-link");
			button.setStyle("align-self:flex-end");
			final Label dica = new Label();
			dica.setSclass("fla-traducao-dica");
			button.addEventListener(Events.ON_CLICK, event -> {
				final String original = origem(parent, context, source);
				if (StringUtils.isBlank(original))
				{
					Clients.showNotification(text("fla.traducao.vazio", "Preencha primeiro o campo em português."), "warning", box,
							"end_center", 5000);
					return;
				}
				try
				{
					final String traduzido = traducao.traduzir(original, "pt", "en");
					textbox.setValue(traduzido);
					listener.onValueChanged(traduzido);
					dica.setValue(text("fla.traducao.dica", "Sugestão da IA a partir do português. Revise antes de seguir."));
				}
				catch (final RuntimeException e)
				{
					LOG.warn("Tradução não feita: {}", e.getMessage());
					Clients.showNotification(StringUtils.defaultIfBlank(e.getMessage(), "Não foi possível traduzir."), "error", box,
							"end_center", 8000);
				}
			});
			box.appendChild(button);
			box.appendChild(textbox);
			box.appendChild(dica);
		}
		else
		{
			box.appendChild(textbox);
		}
		parent.appendChild(box);
	}

	/** Texto em português do produto que está na tela (assistente ou ficha), lido do próprio objeto em edição */
	protected static String origem(final Component parent, final EditorContext<Object> context, final String source)
	{
		final Object item = objetoEmEdicao(parent, context);
		if (!(item instanceof ProductModel))
		{
			return null;
		}
		final ProductModel product = (ProductModel) item;
		return "resumo".equals(source) ? product.getSummary(PT) : product.getName(PT);
	}

	/**
	 * O produto em edição: o parâmetro "parentObject" do editor quando houver; senão, o modelo do widget (newProduct no
	 * assistente, currentObject na ficha), pelo componente Editor mais próximo.
	 */
	protected static Object objetoEmEdicao(final Component parent, final EditorContext<Object> context)
	{
		final Object direct = context.getParameter("parentObject");
		if (direct != null)
		{
			return direct;
		}
		for (Component c = parent; c != null; c = c.getParent())
		{
			try
			{
				final Method method = c.getClass().getMethod("getWidgetInstanceManager");
				final Object wim = method.invoke(c);
				if (wim == null)
				{
					continue;
				}
				final Object model = wim.getClass().getMethod("getModel").invoke(wim);
				final Method getValue = model.getClass().getMethod("getValue", String.class, Class.class);
				for (final String key : new String[] { "newProduct", "currentObject" })
				{
					final Object value = getValue.invoke(model, key, Object.class);
					if (value instanceof ProductModel)
					{
						return value;
					}
				}
			}
			catch (final NoSuchMethodException e)
			{
				// componente sem widget; sobe mais um nível
			}
			catch (final ReflectiveOperationException | RuntimeException e)
			{
				LOG.debug("Objeto em edição não encontrado em {}: {}", c.getClass().getSimpleName(), e.getMessage());
			}
		}
		return null;
	}

	protected static TraducaoService service()
	{
		try
		{
			return Registry.getApplicationContext().getBean("flamengoTraducaoService", TraducaoService.class);
		}
		catch (final RuntimeException e)
		{
			return null;
		}
	}

	protected static String text(final String key, final String fallback)
	{
		final String value = Labels.getLabel(key);
		return StringUtils.isBlank(value) ? fallback : value;
	}
}
