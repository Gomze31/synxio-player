package fr.synxio.player.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import fr.synxio.player.BuildConfig
import fr.synxio.player.R
import fr.synxio.player.ui.components.GlassCard
import fr.synxio.player.ui.components.enterCascade
import fr.synxio.player.ui.components.pressBounce
import fr.synxio.player.ui.theme.EyebrowTextStyle
import fr.synxio.player.ui.theme.LocalGlowEffects
import fr.synxio.player.ui.theme.Radius
import fr.synxio.player.ui.theme.Space
import fr.synxio.player.ui.theme.SynxioShape
import fr.synxio.player.ui.theme.SynxioTheme
import fr.synxio.player.ui.theme.TabularNumbers
import fr.synxio.player.ui.theme.glass
import fr.synxio.player.ui.theme.glowShadow
import fr.synxio.player.ui.theme.synxioTopBarColors

/**
 * À propos : la vitrine de l'app. Logo posé sur la signature, nom en grand, version, puis
 * ce qu'il faut savoir sur les données et où trouver le code.
 *
 * Les liens ne s'affichent que si le dépôt de publication est connu à la compilation :
 * un lien vers nulle part serait pire que pas de lien.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val uriHandler = LocalUriHandler.current
    val repo = BuildConfig.UPDATE_REPO.trim()
    Scaffold(
        containerColor = Color.Transparent,
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                colors = synxioTopBarColors(),
                // Le titre est porté par la vitrine en dessous : la barre reste muette.
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Retour")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.gutter)
                .padding(bottom = Space.xxxl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AboutHero(Modifier.enterCascade(0, key = "about-hero"))

            Spacer(Modifier.height(Space.xxl))

            AboutGroupTitle("L'essentiel", Modifier.enterCascade(1, key = "about-t1"))
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .enterCascade(2, key = "about-c1"),
                contentPadding = Space.xs,
            ) {
                Column {
                    AboutLine(
                        icon = Icons.Rounded.Lock,
                        title = "Tes données restent chez toi",
                        text = "Bibliothèque, playlists, favoris et statistiques sont stockés uniquement sur ton appareil.",
                    )
                    AboutDivider()
                    AboutLine(
                        icon = Icons.Rounded.Code,
                        title = "Projet open source",
                        text = "Développé en Kotlin avec Jetpack Compose.",
                    )
                }
            }

            Spacer(Modifier.height(Space.xl))

            AboutGroupTitle("Liens", Modifier.enterCascade(3, key = "about-t2"))
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .enterCascade(4, key = "about-c2"),
                contentPadding = Space.xs,
            ) {
                Column {
                    if (repo.isNotBlank()) {
                        AboutLine(
                            icon = Icons.Rounded.Code,
                            title = "Code source",
                            text = "github.com/$repo",
                            onClick = { runCatching { uriHandler.openUri("https://github.com/$repo") } },
                        )
                        AboutDivider()
                        AboutLine(
                            icon = Icons.Rounded.NewReleases,
                            title = "Notes de version",
                            text = "Ce qui a changé, version après version",
                            onClick = { runCatching { uriHandler.openUri("https://github.com/$repo/releases") } },
                        )
                        AboutDivider()
                    }
                    AboutLine(
                        icon = Icons.Rounded.Lyrics,
                        title = "Paroles synchronisées",
                        text = "Fournies par LRCLIB, base libre et communautaire",
                        onClick = { runCatching { uriHandler.openUri("https://lrclib.net") } },
                    )
                }
            }
        }
    }
}

/**
 * Vitrine : logo sur un disque à la signature qui éclaire la scène, « Synxio » en
 * display rempli du dégradé, puis la version dans une pilule de verre.
 */
@Composable
private fun AboutHero(modifier: Modifier = Modifier) {
    val colors = SynxioTheme.colors
    val glow = LocalGlowEffects.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = Space.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (glow) {
                // Halo large et diffus : la pochette « allume » le fond, le logo aussi.
                Box(
                    Modifier
                        .size(220.dp)
                        .background(
                            Brush.radialGradient(
                                listOf(colors.brandEnd.copy(alpha = 0.30f), Color.Transparent)
                            )
                        )
                )
            }
            Box(
                modifier = Modifier
                    .size(112.dp)
                    .glowShadow(RoundedCornerShape(Radius.xl), colors.brandEnd, elevation = 30.dp)
                    .clip(RoundedCornerShape(Radius.xl))
                    .background(colors.brandBrush),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(colors.onBrand),
                    modifier = Modifier.size(112.dp),
                )
            }
        }

        Spacer(Modifier.height(Space.lg))
        Text(
            "LECTEUR DE MUSIQUE LOCAL",
            style = EyebrowTextStyle,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(Space.xs))
        Text(
            "Synxio",
            style = MaterialTheme.typography.displayLarge.copy(brush = colors.brandBrushHorizontal),
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(Space.md))
        Text(
            "Version ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.labelLarge.merge(TabularNumbers),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .clip(SynxioShape.pill)
                .glass(SynxioShape.pill, strength = 0.6f)
                .padding(horizontal = Space.lg, vertical = Space.sm),
        )
        Spacer(Modifier.height(Space.lg))
        Text(
            "Ta musique, sur ton téléphone, sans compte ni publicité.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AboutGroupTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        title.uppercase(),
        style = EyebrowTextStyle,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = Space.xs, bottom = Space.sm)
            .semantics { heading() },
    )
}

@Composable
private fun AboutDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 64.dp, end = Space.md),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
    )
}

/**
 * Ligne d'information, cliquable quand elle mène quelque part (lien externe signalé par
 * une flèche sortante, pour qu'on sache qu'on quitte l'app).
 */
@Composable
private fun AboutLine(
    icon: ImageVector,
    title: String,
    text: String,
    onClick: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Space.touchTarget)
            .clip(SynxioShape.cardSm)
            .then(
                if (onClick != null) {
                    Modifier
                        .pressBounce(0.98f)
                        .clickable(role = Role.Button, onClickLabel = "Ouvrir", onClick = onClick)
                } else Modifier
            )
            .padding(horizontal = Space.md, vertical = Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(scheme.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        }
        if (onClick != null) {
            Spacer(Modifier.width(Space.sm))
            Icon(
                Icons.AutoMirrored.Rounded.OpenInNew,
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
