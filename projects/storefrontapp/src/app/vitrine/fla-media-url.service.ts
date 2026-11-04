import { Injectable } from '@angular/core';
import { OccConfig } from '@spartacus/core';

/** Endereço completo de uma mídia do SAP Commerce (/medias/...), como o Spartacus monta as imagens */
@Injectable({ providedIn: 'root' })
export class FlaMediaUrlService {
  constructor(protected config: OccConfig) {}

  url(path?: string): string | undefined {
    if (!path) {
      return undefined;
    }
    if (/^(https?:)?\/\//.test(path) || path.startsWith('data:')) {
      return path;
    }
    const base =
      this.config.backend?.media?.baseUrl ??
      this.config.backend?.occ?.baseUrl ??
      '';
    return base + path;
  }
}

/** Imagem padrão do jogo sem foto cadastrada */
export const FLA_DEFAULT_MATCH_IMAGE = 'assets/vitrine/jogo-padrao.svg';

/** Melhor imagem da lista do OCC para card largo: zoom (515 px) > product (300 px) > qualquer */
export function bestImageUrl(
  images?: { format?: string; url?: string }[]
): string | undefined {
  const list = images ?? [];
  return (
    list.find((i) => i.format === 'zoom')?.url ??
    list.find((i) => i.format === 'product')?.url ??
    list[0]?.url
  );
}
