package app.vanillify.device

/** Why one app depends on another, strongest first. */
enum class LinkKind(val strong: Boolean, val label: String) {
    /** Loads code the other app provides; may not start without it. */
    LIBRARY(true, "Code library"),
    /** Holds a permission only same-signature apps can get: part of the same system. */
    PERMISSION(true, "Protected feature"),
    /** The community list says so. */
    COMMUNITY(true, "Community list"),
    /** Holds an ordinary custom permission: an optional feature, works without it. */
    FEATURE(false, "Optional feature"),
}

data class Link(val pkg: String, val kind: LinkKind, val via: Set<String>)

/** Something the system relies on an app for right now. */
enum class SystemRole(val label: String, val warning: String, val critical: Boolean) {
    HOME("Your home screen", "Without a home screen the phone can be left with no way to open apps. Install and choose another one first.", true),
    KEYBOARD("Your keyboard", "Removing the keyboard you're using can leave you unable to type. Switch keyboards first.", true),
    DIALER("Your phone app", "You won't be able to make calls until you choose another phone app.", true),
    SMS("Your SMS app", "Text messages won't have an app until you choose another one.", false),
    BROWSER("Your default browser", "Links won't open until you choose another browser.", false),
    ASSISTANT("Your digital assistant", "Long-pressing power or home won't open an assistant.", false),
    WALLET("Your wallet app", "Tap to pay won't work until you choose another wallet.", false),
    ACCESSIBILITY("An accessibility service you use", "The accessibility feature it provides will stop.", false),
    NOTIFICATIONS("Reads your notifications", "Whatever it does with notifications stops, such as showing them on a watch, in the car or on lights.", false),
    DEVICE_ADMIN("A device admin", "Device admins usually can't be removed until turned off in Settings › Security.", false),
}

/** What the system uses apps for and which apps are add-ons (overlays). Needs Shizuku to read. */
data class SystemUse(
    val roles: Map<String, Set<SystemRole>> = emptyMap(),
    /** Overlay package → the app it changes. */
    val overlays: Map<String, String> = emptyMap(),
)

data class AppLinks(
    val roles: Set<SystemRole>,
    /** Apps this one needs to work. */
    val needs: List<Link>,
    /** Apps that need this one. */
    val neededBy: List<Link>,
    /** Optional features this app gets from others. */
    val uses: List<Link>,
    /** Apps that get optional features from this one. */
    val usedBy: List<Link>,
    /** Same identity, permissions and data. */
    val sharesIdWith: List<String>,
    /** This app is an add-on (overlay) that changes [overlayFor]. */
    val overlayFor: String?,
    /** Add-ons that change this app. */
    val overlays: List<String>,
) {
    val isEmpty get() = roles.isEmpty() && needs.isEmpty() && neededBy.isEmpty() && uses.isEmpty() &&
        usedBy.isEmpty() && sharesIdWith.isEmpty() && overlayFor == null && overlays.isEmpty()
}

/**
 * How the apps on this phone depend on each other, built from what Android records:
 * shared libraries, custom permissions, shared identities and overlays, plus the
 * community list and what the system uses apps for. Pure, so it's unit-tested.
 */
class LinkIndex(
    apps: List<InstalledApp>,
    libraries: List<LibraryUse>,
    /** Community list: package → (dependencies, neededBy). */
    community: (String) -> Pair<List<String>, List<String>>?,
    private val system: SystemUse = SystemUse(),
) {
    private val outgoing = HashMap<String, MutableMap<String, Link>>()
    private val incoming = HashMap<String, MutableMap<String, Link>>()
    private val sharedIds = HashMap<String, MutableList<String>>()
    private val sharedIdOf = HashMap<String, String>()
    private val overlaysOf = HashMap<String, MutableList<String>>()
    private val present = apps.mapTo(HashSet()) { it.pkg }

    init {
        // Custom permission → (owner, signature-only). Android's own ("android") aren't links.
        val owners = HashMap<String, Pair<String, Boolean>>()
        for (app in apps) for ((perm, signature) in app.defines) owners.putIfAbsent(perm, app.pkg to signature)

        for (app in apps) {
            for (perm in app.requested) {
                val (owner, signature) = owners[perm] ?: continue
                if (owner == app.pkg || owner == "android") continue
                link(app.pkg, owner, if (signature) LinkKind.PERMISSION else LinkKind.FEATURE, perm.substringAfterLast('.'))
            }
            app.sharedUserId?.let {
                sharedIds.getOrPut(it) { mutableListOf() } += app.pkg
                sharedIdOf[app.pkg] = it
            }
            community(app.pkg)?.let { (dependencies, neededBy) ->
                dependencies.filter { it in present }.forEach { link(app.pkg, it, LinkKind.COMMUNITY, null) }
                neededBy.filter { it in present }.forEach { link(it, app.pkg, LinkKind.COMMUNITY, null) }
            }
        }
        for (lib in libraries) for (user in lib.users) link(user, lib.provider, LinkKind.LIBRARY, lib.name)
        for ((overlay, target) in system.overlays) overlaysOf.getOrPut(target) { mutableListOf() } += overlay
    }

    /** [from] needs [to]; the strongest reason wins, the details add up. */
    private fun link(from: String, to: String, kind: LinkKind, via: String?) {
        if (from == to) return
        add(outgoing.getOrPut(from) { HashMap() }, to, kind, via)
        add(incoming.getOrPut(to) { HashMap() }, from, kind, via)
    }

    private fun add(map: MutableMap<String, Link>, pkg: String, kind: LinkKind, via: String?) {
        val old = map[pkg]
        val vias = (old?.via.orEmpty() + listOfNotNull(via)).toSet()
        map[pkg] = Link(pkg, if (old == null || kind.ordinal < old.kind.ordinal) kind else old.kind, vias)
    }

    fun of(pkg: String): AppLinks {
        val out = outgoing[pkg]?.values.orEmpty().sortedBy { it.kind.ordinal }
        val inc = incoming[pkg]?.values.orEmpty().sortedBy { it.kind.ordinal }
        val sharedId = sharedIdOf[pkg]?.let { sharedIds[it] }
        return AppLinks(
            roles = system.roles[pkg].orEmpty(),
            needs = out.filter { it.kind.strong },
            neededBy = inc.filter { it.kind.strong },
            uses = out.filterNot { it.kind.strong },
            usedBy = inc.filterNot { it.kind.strong },
            sharesIdWith = sharedId?.filter { it != pkg }.orEmpty(),
            overlayFor = system.overlays[pkg],
            overlays = overlaysOf[pkg].orEmpty(),
        )
    }
}
