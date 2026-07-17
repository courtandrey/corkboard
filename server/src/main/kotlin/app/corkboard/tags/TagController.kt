package app.corkboard.tags

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

data class TagItem(
    val name: String,
    val slug: String,
    val usageCount: Int,
)

data class TagListResponse(val items: List<TagItem>)

@RestController
@RequestMapping("/api/v1/tags")
class TagController(private val tags: TagService) {

    @GetMapping
    fun list(
        @RequestParam(required = false) q: String?,
        @RequestParam(defaultValue = "10") limit: Int,
    ): TagListResponse = TagListResponse(tags.search(q, limit.coerceIn(1, 50)))
}
