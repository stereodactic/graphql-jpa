package org.crygier.graphql;

/**
 *
 * @author chris
 */
public class PaginationResult {
	
	private Long totalElements;
	
	private Long totalPages;
	
	private Integer pageSize;
	
	private Integer page;
	
	public PaginationResult(Long totalElements, Long totalPages, Integer pageSize, Integer page) {
		this.totalElements = totalElements;
		this.totalPages = totalPages;
		this.pageSize = pageSize;
		this.page = page;
	}

	public Long getTotalElements() {
		return totalElements;
	}

	public void setTotalElements(Long totalElements) {
		this.totalElements = totalElements;
	}

	public Long getTotalPages() {
		return totalPages;
	}

	public void setTotalPages(Long totalPages) {
		this.totalPages = totalPages;
	}

	public Integer getPageSize() {
		return pageSize;
	}

	public void setPageSize(Integer pageSize) {
		this.pageSize = pageSize;
	}

	public Integer getPage() {
		return page;
	}

	public void setPage(Integer page) {
		this.page = page;
	}
	
}
