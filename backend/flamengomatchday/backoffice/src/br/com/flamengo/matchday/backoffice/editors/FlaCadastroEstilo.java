package br.com.flamengo.matchday.backoffice.editors;

import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.HtmlBasedComponent;
import org.zkoss.zul.Style;
import org.zkoss.zul.Window;


/**
 * Visual do assistente "Novo produto" e da aba "Cadastro" (08/11, mockup aprovado): rótulo em cima do campo, duas
 * colunas, campos cinza com linha embaixo, link "Traduzir com IA", avisos azuis, fotos lado a lado e botões no padrão
 * Horizon. Tudo vale só dentro da janela marcada com {@code fla-cad-janela} (assistente) ou do bloco do editor (aba), então
 * o resto do Backoffice não muda.
 */
public final class FlaCadastroEstilo
{
	public static final String JANELA = "fla-cad-janela";
	private static final String ATRIBUTO = "flaCadastroEstilo";

	/** Ícone de informação dos avisos (SVG em linha, sem arquivo) */
	public static final String ICONE_INFO = "<svg width=\"16\" height=\"16\" viewBox=\"0 0 16 16\" aria-hidden=\"true\" "
			+ "style=\"flex-shrink:0\"><circle cx=\"8\" cy=\"8\" r=\"7\" stroke=\"#0064d9\" stroke-width=\"1.5\" fill=\"none\"/>"
			+ "<path d=\"M8 7v4M8 4.5v.5\" stroke=\"#0064d9\" stroke-width=\"1.6\"/></svg>";

	private static final String CSS = ""
			// janela e cabeçalho de passos
			+ ".fla-cad-janela .yw-wizard-breadcrumb-cnt,.fla-cad-janela .yw-wizard-breadcrumb *{word-break:normal}"
			+ ".fla-cad-janela .yw-wizard-breadcrumb-container{display:flex;align-items:center;gap:10px}"
			+ ".fla-cad-janela .yw-wizard-breadcrumb-label-container{white-space:nowrap;flex:0 0 auto}"
			+ ".fla-cad-janela .yw-wizard-breadcrumb-indicator{width:24px;height:24px;min-width:24px;border-radius:50%;border:1px solid #758ca4;"
			+ "color:#556b82;font-size:13px;display:flex;align-items:center;justify-content:center;box-sizing:border-box;background:#fff}"
			+ ".fla-cad-janela .yw-wizard-breadcrumb-active .yw-wizard-breadcrumb-indicator{background:#0070f2;border-color:#0070f2;color:#fff;font-weight:600}"
			+ ".fla-cad-janela .yw-wizard-breadcrumb-before .yw-wizard-breadcrumb-indicator{border-color:#0070f2;color:#0070f2}"
			+ ".fla-cad-janela .yw-wizard-breadcrumb-label{font-size:13px;font-weight:600;color:#1d2d3e}"
			+ ".fla-cad-janela .yw-wizard-breadcrumb-active .yw-wizard-breadcrumb-label{color:#0064d9}"
			+ ".fla-cad-janela .yw-wizard-breadcrumb-sublabel{font-size:11px;color:#556b82}"
			// conteúdo em duas colunas, rótulo em cima
			+ ".fla-cad-janela .yw-wizard-content.fla-cad-conteudo{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));"
			+ "gap:16px 20px;align-content:start;padding:20px 24px;box-sizing:border-box;overflow:auto}"
			+ ".fla-cad-janela .fla-cad-conteudo.fla-cad-passo2{grid-template-columns:minmax(0,1fr)}"
			+ ".fla-cad-janela .fla-cad-conteudo.fla-cad-passo4{grid-template-columns:200px repeat(3,minmax(0,1fr));gap:16px 14px}"
			+ ".fla-cad-janela .fla-cad-conteudo > div{min-width:0}"
			+ ".fla-cad-janela .fla-cad-conteudo .yw-wizard-property{position:relative;display:flex;flex-direction:column;margin:0;padding:0;width:auto}"
			+ ".fla-cad-janela .fla-cad-conteudo .yw-wizard-property > *{width:100%;max-width:none}"
			+ ".fla-cad-janela .fla-cad-conteudo .yw-wizard-property-label,.fla-cad-rotulo{display:block;float:none;width:auto;text-align:left;"
			+ "font-size:13px;color:#556b82;margin:0 0 6px 0;padding:0;line-height:18px}"
			+ ".fla-cad-janela .fla-cad-conteudo .yw-wizard-property-label-mandatory:after{content:' *';color:#d1003b}"
			+ ".fla-cad-janela .fla-cad-conteudo input.z-textbox,.fla-cad-janela .fla-cad-conteudo textarea.z-textbox,"
			+ ".fla-cad-janela .fla-cad-conteudo .z-doublebox,.fla-cad-janela .fla-cad-conteudo .z-intbox,"
			+ ".fla-cad-janela .fla-cad-conteudo .z-longbox,.fla-cad-janela .fla-cad-conteudo .z-decimalbox,.fla-cad-busca{"
			+ "box-sizing:border-box;width:100% !important;height:36px;padding:0 12px;border:0;border-bottom:1px solid #556b82;"
			+ "background:#eff1f2;border-radius:6px 6px 0 0;font-size:14px;color:#1d2d3e;box-shadow:none}"
			+ ".fla-cad-janela .fla-cad-conteudo textarea.z-textbox{height:84px;padding:8px 12px;resize:none}"
			+ ".fla-cad-janela .fla-cad-conteudo input.z-textbox:focus,.fla-cad-janela .fla-cad-conteudo textarea.z-textbox:focus,"
			+ ".fla-cad-busca:focus{outline:none;border-bottom:2px solid #0064d9}"
			// blocos próprios: somente leitura, dica, aviso
			+ ".fla-cad-ro{box-sizing:border-box;height:36px;line-height:34px;padding:0 12px;border:1px solid #d5dadd;border-radius:6px;"
			+ "background:#fff;font-size:14px;color:#1d2d3e;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}"
			+ ".fla-cad-dica{font-size:12px;color:#556b82;margin-top:-10px}"
			+ ".fla-cad-aviso{grid-column:1/-1;display:flex;gap:10px;align-items:flex-start;padding:12px 14px;background:#ebf8ff;"
			+ "border-radius:8px;font-size:13px;line-height:18px;color:#1d2d3e}"
			// link "Traduzir com IA"
			+ ".fla-ia-link.z-button,.fla-ia-link{background:transparent !important;border:0 !important;box-shadow:none !important;padding:0 !important;"
			+ "min-width:0;height:auto !important;line-height:18px;font-size:13px;font-weight:600;color:#0064d9 !important;cursor:pointer}"
			+ ".fla-ia-link:hover{color:#0040b0 !important;text-decoration:underline}"
			+ ".fla-ia-link:focus{outline:none}.fla-ia-link:focus-visible{outline:2px solid #0064d9;outline-offset:2px;border-radius:4px}"
			+ ".fla-ia-link:before{content:'';display:inline-block;width:12px;height:12px;margin-right:4px;vertical-align:-1px;"
			+ "background:url(\"data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 12 12'%3E%3Cpath d='M6 .5l1.3 3.2 3.2 1.3-3.2 1.3L6 9.5 4.7 6.3 1.5 5l3.2-1.3z' fill='%230064d9'/%3E%3C/svg%3E\") no-repeat center/12px}"
			+ ".fla-cad-janela .fla-cad-conteudo .fla-traducao-box{position:static}"
			+ ".fla-cad-janela .fla-cad-conteudo .fla-ia-link{position:absolute;top:0;right:0}"
			+ ".fla-traducao-dica{font-size:12px;color:#556b82}"
			// lista de categorias
			+ ".fla-cat-lista{border:1px solid #d5dadd;border-radius:8px;overflow:auto;max-height:236px;background:#fff}"
			+ ".fla-cat-linha{display:flex;align-items:center;gap:12px;padding:10px 14px;border-bottom:1px solid #e5e9ec;font-size:14px;cursor:pointer}"
			+ ".fla-cat-linha:last-child{border-bottom:0}"
			+ ".fla-cat-linha:hover{background:#f5f6f7}"
			+ ".fla-cat-linha.fla-cat-sel{background:#ebf8ff}"
			+ ".fla-cat-radio{width:16px;height:16px;min-width:16px;border-radius:50%;border:1px solid #556b82;box-sizing:border-box;background:#fff}"
			+ ".fla-cat-sel .fla-cat-radio{border:5px solid #0070f2}"
			+ ".fla-cat-sep{color:#758ca4}"
			+ ".fla-cat-cod{margin-left:auto;font-size:12px;color:#556b82;font-family:ui-monospace,Consolas,monospace}"
			+ ".fla-cat-vazio{padding:12px 14px;font-size:13px;color:#556b82}"
			// fotos
			+ ".fla-cad-janela .fla-cad-passo4 .fla-image-editor{max-width:none !important}"
			+ ".fla-cad-janela .fla-cad-passo4 .fla-image-editor > div:first-child{width:100% !important}"
			+ ".fla-cad-janela .fla-cad-passo4 .fla-image-dica{display:none}"
			+ ".fla-image-editor .z-button{font-size:13px}"
			// botões do rodapé: Voltar à esquerda; Cancelar e Próximo/Concluir à direita, o principal em azul
			+ ".fla-cad-janela .yw-wizard-navigation{display:flex;align-items:center;min-height:60px;padding:0 20px;"
			+ "border-top:1px solid #e5e9ec;box-sizing:border-box}"
			+ ".fla-cad-janela .yw-wizard-navigation .yw-left-pack{display:flex;gap:8px;float:none}"
			+ ".fla-cad-janela .yw-wizard-navigation .yw-right-pack{display:flex;flex-direction:row-reverse;gap:8px;margin-left:auto;float:none}"
			+ ".fla-cad-janela .yw-wizard-navigation .z-button{height:36px;padding:0 16px;border-radius:8px;border:1px solid transparent;"
			+ "font-size:14px;font-weight:600;background:#eaecee;color:#1d2d3e;box-shadow:none;margin:0}"
			+ ".fla-cad-janela .yw-wizard-navigation .yw-right-pack > .z-button:first-child,"
			+ ".fla-cad-janela .yw-wizard-navigation .yw-right-pack > :first-child .z-button,"
			+ ".fla-cad-janela .yw-wizard-navigation .z-button.yw-btn-primary{background:#0070f2;color:#fff}"
			+ ".fla-cad-janela .yw-wizard-navigation .z-button[disabled]{opacity:.45}";

	private FlaCadastroEstilo()
	{
	}

	/**
	 * Põe o estilo uma vez na janela do assistente (que dura todos os passos) e marca a janela; fora de janela (aba
	 * "Cadastro" da ficha), põe no próprio componente.
	 */
	public static void aplicar(final Component near)
	{
		if (near == null)
		{
			return;
		}
		final Window window = janela(near);
		if (window != null)
		{
			marcar(window, JANELA);
			if (window.getAttribute(ATRIBUTO) == null)
			{
				window.appendChild(style());
				window.setAttribute(ATRIBUTO, Boolean.TRUE);
			}
			return;
		}
		if (near.getAttribute(ATRIBUTO) == null)
		{
			near.appendChild(style());
			near.setAttribute(ATRIBUTO, Boolean.TRUE);
		}
	}

	/** Acrescenta uma classe ao componente sem perder as que ele já tem */
	public static void marcar(final Component component, final String... classes)
	{
		if (!(component instanceof HtmlBasedComponent))
		{
			return;
		}
		final HtmlBasedComponent html = (HtmlBasedComponent) component;
		String sclass = html.getSclass() == null ? "" : html.getSclass();
		for (final String c : classes)
		{
			if (!(" " + sclass + " ").contains(" " + c + " "))
			{
				sclass = (sclass + " " + c).trim();
			}
		}
		html.setSclass(sclass);
	}

	protected static Window janela(final Component near)
	{
		for (Component c = near; c != null; c = c.getParent())
		{
			if (c instanceof Window)
			{
				return (Window) c;
			}
		}
		return null;
	}

	protected static Style style()
	{
		final Style style = new Style();
		style.setContent(CSS);
		return style;
	}
}
