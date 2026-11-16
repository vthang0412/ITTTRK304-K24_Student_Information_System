import { useMemo, useState } from 'react';

export const PAGE_SIZE_OPTIONS = [10, 20, 50, 100];

export const usePaginatedList = (items) => {
  const [page, setPage] = useState(1);
  const [pageSize, setPageSizeState] = useState(10);
  const totalItems = items.length;
  const totalPages = Math.ceil(totalItems / pageSize);
  const currentPage = totalPages === 0 ? 1 : Math.min(page, totalPages);
  const pageItems = useMemo(
    () => items.slice((currentPage - 1) * pageSize, currentPage * pageSize),
    [items, currentPage, pageSize],
  );
  const setPageSize = (value) => {
    setPageSizeState(Number(value));
    setPage(1);
  };

  return {
    page: currentPage,
    setPage,
    pageSize,
    setPageSize,
    totalItems,
    totalPages,
    pageItems,
  };
};
