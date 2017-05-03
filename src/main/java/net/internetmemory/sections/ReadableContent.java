package net.internetmemory.sections;

import java.util.List;

/**
 * Readable contents (HTML and text contents without boilerplate, URLs of images exclude ad images)
 * extracted from a Web page
 */
public class ReadableContent {

    private String title;
    private String html;
    private String text;
    private String mainImageUrl;
    private List<String> imageUrls;

    public ReadableContent(String title, String html, String text, String mainImageUrl, List<String> imageUrls) {
        this.title = title;
        this.html = html;
        this.text = text;
        this.mainImageUrl = mainImageUrl;
        this.imageUrls = imageUrls;
    }

    /**
     * Gets content title
     * @return content title
     */
    public String getTitle() {
        return title;
    }

    /**
     * Gets cleaned HTML without boilerplate
     * @return cleaned HTML without boilerplate
     */
    public String getHtml() {
        return html;
    }

    /**
     * Gets plain text extracted from the main content of a page
     * @return plain text extracted from the main content of a page
     */
    public String getText() {
        return text;
    }

    /**
     * Gets URL of the main image of a page
     * @return URL of the main image of a page
     */
    public String getMainImageUrl() {
        return mainImageUrl;
    }

    /**
     * Gets URLs of images exclude not important images (e.g. ad images)
     * @return GET URLs of important images
     */
    public List<String> getImageUrls() {
        return imageUrls;
    }

    @Override
    public String toString() {
        return "ReadableContent{" +
                "title='" + title + '\'' +
                ", html='" + html + '\'' +
                ", text='" + text + '\'' +
                ", mainImageUrl='" + mainImageUrl + '\'' +
                ", imageUrls=" + imageUrls +
                '}';
    }
}
