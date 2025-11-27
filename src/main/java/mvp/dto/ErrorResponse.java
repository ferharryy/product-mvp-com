package mvp.dto;

public class ErrorResponse {
    public String error;
    public String detail;

    public ErrorResponse(String error, String detail) {
        this.error = error;
        this.detail = detail;
    }
}
