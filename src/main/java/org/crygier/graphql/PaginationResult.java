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
	
	private Object parent;
	
	private String fieldName;
	
	public PaginationResult(Long totalElements, Long totalPages, Integer pageSize, Integer page, Object parent, String fieldName) {
		this.totalElements = totalElements;
		this.totalPages = totalPages;
		this.pageSize = pageSize;
		this.page = page;
		this.parent = parent;
		this.fieldName = fieldName;
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

	public Object getParent() {
		return parent;
	}

	public void setParent(Object parent) {
		this.parent = parent;
	}

	public String getFieldName() {
		return fieldName;
	}

	public void setFieldName(String fieldName) {
		this.fieldName = fieldName;
	}
}
