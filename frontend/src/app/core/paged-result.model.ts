export interface PagedResult<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number; // current page index, zero-based
  size: number;
}
