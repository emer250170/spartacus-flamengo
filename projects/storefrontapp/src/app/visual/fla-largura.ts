import { WindowRef } from '@spartacus/core';

/**
 * Largura do conteúdo como no site oficial (08/11, Emerson): a partir de 992px o conteúdo ocupa a tela
 * menos 64px de cada lado, até 1472px (o site para em 1600px com as margens); abaixo, 16px de cada lado.
 * O tema calcula --cx-page-width-max com --fla-vw, a largura útil da janela (sem a barra de rolagem, que o
 * 100vw incluiria e desalinharia o conteúdo em metade da barra). Aqui só mantém --fla-vw atualizado.
 */
export function startFlaLargura(winRef: WindowRef): () => void {
  return () => {
    const doc = winRef.document;
    const win = winRef.nativeWindow;
    if (!winRef.isBrowser() || !doc?.documentElement || !win) {
      return;
    }
    const root = doc.documentElement;
    let ultimo = -1;
    const medir = () => {
      const w = root.clientWidth;
      if (w && w !== ultimo) {
        ultimo = w;
        root.style.setProperty('--fla-vw', w + 'px');
      }
    };
    medir();
    win.addEventListener('resize', medir, { passive: true });
    // a barra de rolagem aparece/some quando a página cresce: a largura útil muda sem "resize"
    const Ro = (win as unknown as { ResizeObserver?: typeof ResizeObserver }).ResizeObserver;
    if (Ro) {
      new Ro(medir).observe(root);
    }
  };
}
