import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { OccEndpointsService } from '@spartacus/core';
import { Observable, forkJoin, of } from 'rxjs';
import { catchError, map, shareReplay, switchMap } from 'rxjs/operators';

export interface StoreHours {
  day: string;
  hours: string;
  closed: boolean;
}

/** Loja Flamengo com o estoque de todos os itens que vão para retirada */
export interface PickupStore {
  name: string;
  displayName: string;
  address: string;
  distance?: string;
  distanceKm: number;
  /** menor estoque entre os itens (undefined = disponível sem número) */
  stock?: number;
  inStock: boolean;
  hours: StoreHours[];
  /** "Aberta hoje até 22:00" / "Fechada hoje" */
  today?: string;
}

export interface GeoPoint {
  latitude: number;
  longitude: number;
  /** "Copacabana · Rio de Janeiro/RJ" */
  label?: string;
}

/** Centro do Rio: busca inicial, antes de o torcedor informar CEP ou localização */
export const RIO: GeoPoint = { latitude: -22.9068, longitude: -43.1729, label: 'Rio de Janeiro/RJ' };

const DAYS = ['dom', 'seg', 'ter', 'qua', 'qui', 'sex', 'sab'];

/**
 * Busca das lojas de retirada para o checkout (mockup "Checkout — Entrega"): CEP, cidade ou
 * endereço viram coordenadas (BrasilAPI para o CEP e OpenStreetMap/Nominatim para o lugar, pelo
 * navegador; o servidor não tem geocodificação) e as lojas vêm da API de estoque por loja
 * (/products/{code}/stock), uma consulta por item, juntando o estoque de todos os itens.
 */
@Injectable({ providedIn: 'root' })
export class FlaStoreSearchService {
  constructor(
    protected http: HttpClient,
    protected occEndpoints: OccEndpointsService
  ) {}

  /** CEP (8 números) ou texto livre → coordenadas; null se não achou */
  geocode(query: string): Observable<GeoPoint | null> {
    const q = (query ?? '').trim();
    const cep = q.replace(/\D/g, '');
    if (/^\d{8}$/.test(cep) && q.replace(/[\d\s.-]/g, '') === '') {
      return this.http
        .get<any>(`https://brasilapi.com.br/api/cep/v2/${cep}`)
        .pipe(
          catchError(() => of(null)),
          switchMap((c) => {
            if (!c?.city) {
              return of(null);
            }
            const label = [c.neighborhood, `${c.city}/${c.state}`].filter((x) => !!x).join(' · ');
            const fallback: GeoPoint | null = c.location?.coordinates?.latitude
              ? {
                  latitude: Number(c.location.coordinates.latitude),
                  longitude: Number(c.location.coordinates.longitude),
                  label,
                }
              : null;
            return this.nominatim([c.neighborhood, c.city, c.state].filter((x) => !!x).join(', ')).pipe(
              map((p) => (p ? { ...p, label } : fallback))
            );
          })
        );
    }
    return this.nominatim(q);
  }

  protected nominatim(q: string): Observable<GeoPoint | null> {
    if (!q) {
      return of(null);
    }
    return this.http
      .get<any[]>('https://nominatim.openstreetmap.org/search', {
        params: { format: 'json', countrycodes: 'br', limit: '1', q },
      })
      .pipe(
        map((r) =>
          r?.[0]
            ? {
                latitude: Number(r[0].lat),
                longitude: Number(r[0].lon),
                label: String(r[0].display_name ?? q).split(',').slice(0, 2).join(' ·'),
              }
            : null
        ),
        catchError(() => of(null))
      );
  }

  protected names = new Map<string, Observable<string>>();

  /** Nome da loja ("Loja Flamengo Copacabana") pelo código; sem resposta, o próprio código */
  storeName(name: string): Observable<string> {
    if (!this.names.has(name)) {
      const base = this.occEndpoints.getBaseUrl();
      this.names.set(
        name,
        this.http.get<any>(`${base}/stores/${encodeURIComponent(name)}`, { params: { fields: 'DEFAULT' } }).pipe(
          map((s) => (s?.displayName as string) || name),
          catchError(() => of(name)),
          shareReplay(1)
        )
      );
    }
    return this.names.get(name) as Observable<string>;
  }

  /** Lojas perto do ponto, com o estoque somado de todos os itens (o menor deles) */
  stores(point: GeoPoint, productCodes: string[]): Observable<PickupStore[]> {
    const codes = [...new Set(productCodes)].filter((c) => !!c);
    if (!codes.length) {
      return of([]);
    }
    const base = this.occEndpoints.getBaseUrl();
    return forkJoin(
      codes.map((code) =>
        this.http
          .get<any>(`${base}/products/${encodeURIComponent(code)}/stock`, {
            params: {
              latitude: String(point.latitude),
              longitude: String(point.longitude),
              fields: 'FULL',
              pageSize: '20',
            },
          })
          .pipe(
            map((r) => (r?.stores ?? []) as any[]),
            catchError(() => of([] as any[]))
          )
      )
    ).pipe(
      map((lists) => {
        const byName = new Map<string, PickupStore>();
        const seen = new Map<string, number>();
        lists.forEach((stores) =>
          stores.forEach((st) => {
            seen.set(st.name, (seen.get(st.name) ?? 0) + 1);
            const level: number | undefined = st.stockInfo?.stockLevel;
            const ok = st.stockInfo?.stockLevelStatus !== 'outOfStock' && (level === undefined || level > 0);
            const prev = byName.get(st.name);
            if (prev) {
              prev.inStock = prev.inStock && ok;
              if (level !== undefined) {
                prev.stock = prev.stock === undefined ? level : Math.min(prev.stock, level);
              }
              return;
            }
            const hours: StoreHours[] = (st.openingHours?.weekDayOpeningList ?? []).map((d: any) => ({
              day: d.weekDay,
              hours: d.closed ? '' : `${d.openingTime?.formattedHour} às ${d.closingTime?.formattedHour}`,
              closed: !!d.closed,
            }));
            byName.set(st.name, {
              name: st.name,
              displayName: st.displayName ?? st.name,
              address: st.address?.formattedAddress ?? '',
              distance: st.formattedDistance,
              distanceKm: Number(String(st.formattedDistance ?? '').replace(/[^\d.,]/g, '').replace(/,/g, '')) || 0,
              stock: level,
              inStock: ok,
              hours,
              today: this.today(hours),
            });
          })
        );
        // loja que não apareceu para algum item não tem esse item: sem estoque
        byName.forEach((st, name) => {
          if ((seen.get(name) ?? 0) < codes.length) {
            st.inStock = false;
            st.stock = 0;
          }
        });
        return [...byName.values()].sort((a, b) => a.distanceKm - b.distanceKm);
      })
    );
  }

  protected today(hours: StoreHours[]): string | undefined {
    const key = DAYS[new Date().getDay()];
    const norm = (s: string) =>
      (s ?? '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().slice(0, 3);
    const d = hours.find((h) => norm(h.day) === key);
    if (!d) {
      return undefined;
    }
    return d.closed ? 'closed' : d.hours.split(' às ')[1];
  }
}
