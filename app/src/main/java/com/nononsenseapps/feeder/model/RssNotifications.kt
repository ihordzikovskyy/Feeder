package com.nononsenseapps.feeder.model

import android.annotation.TargetApi
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Context.NOTIFICATION_SERVICE
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.Browser.EXTRA_CREATE_NEW_TAB
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.TaskStackBuilder
import com.nononsenseapps.feeder.R
import com.nononsenseapps.feeder.db.COL_LINK
import com.nononsenseapps.feeder.db.URI_FEEDITEMS
import com.nononsenseapps.feeder.db.URI_FEEDS
import com.nononsenseapps.feeder.db.room.AppDatabase
import com.nononsenseapps.feeder.db.room.FeedItemWithFeed
import com.nononsenseapps.feeder.ui.*
import com.nononsenseapps.feeder.util.ARG_FEEDTITLE
import com.nononsenseapps.feeder.util.notificationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


const val notificationId = 73583
const val channelId = "feederNotifications"

suspend fun notify(appContext: Context) = withContext(Dispatchers.Default) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        createNotificationChannel(appContext)
    }

    val nm = appContext.notificationManager

    val feedItems = getItemsToNotify(appContext)

    val notifications: List<Pair<Int, Notification>> = if (feedItems.isEmpty()) {
        emptyList()
    } else {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N || feedItems.size < 4) {
            // Cancel inbox notification if present
            nm.cancel(notificationId)
            // Platform automatically bundles 4 or more notifications
            feedItems.map {
                it.id.toInt() to singleNotification(appContext, it)
            }
        } else {
            // In this case, also cancel any individual notifications
            feedItems.forEach {
                nm.cancel(it.id.toInt())
            }
            // Use an inbox style notification to bundle many notifications together
            listOf(notificationId to inboxNotification(appContext, feedItems))
        }
    }

    notifications.forEach { (id, notification) ->
        nm.notify(id, notification)
    }
}

suspend fun cancelNotification(context: Context, feedItemId: Long) = withContext(Dispatchers.Default) {
    val nm = context.notificationManager
    nm.cancel(feedItemId.toInt())

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
        notify(context)
    }
}

/**
 * This is an update operation if channel already exists so it's safe to call multiple times
 */
@TargetApi(Build.VERSION_CODES.O)
@RequiresApi(Build.VERSION_CODES.O)
private fun createNotificationChannel(context: Context) {
    val name = context.getString(R.string.notification_channel_name)
    val description = context.getString(R.string.notification_channel_description)

    val notificationManager: NotificationManager = context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager

    val channel = NotificationChannel(channelId, name, NotificationManager.IMPORTANCE_LOW)
    channel.description = description

    notificationManager.createNotificationChannel(channel)
}

private fun singleNotification(context: Context, item: FeedItemWithFeed): Notification {
    val style = NotificationCompat.BigTextStyle()
    val title = item.plainTitle
    val text = item.feedDisplayTitle

    style.bigText(text)
    style.setBigContentTitle(title)

    val contentIntent = when (item.description.isBlank()) {
        true -> {
            val i = Intent(context, FeedActivity::class.java)
            i.data = Uri.withAppendedPath(URI_FEEDS, "${item.feedId}")
            i.flags = FLAG_ACTIVITY_CLEAR_TASK or FLAG_ACTIVITY_NEW_TASK
            PendingIntent.getActivity(context, item.id.toInt(), i,
                    PendingIntent.FLAG_UPDATE_CURRENT)
        }
        false -> {
            val i = Intent(context, ReaderActivity::class.java)
            ReaderActivity.setRssExtras(i, item)
            i.data = Uri.withAppendedPath(URI_FEEDITEMS, "${item.id}")
            val stackBuilder = TaskStackBuilder.create(context)
            // Add the parent of the specified activity - as stated in the manifest
            stackBuilder.addParentStack(ReaderActivity::class.java)
            stackBuilder.addNextIntent(i)
            // Now, modify the parent intent so that it navigates to the appropriate feed
            val parentIntent = stackBuilder.editIntentAt(0)
            if (parentIntent != null) {
                parentIntent.data = Uri.withAppendedPath(URI_FEEDS, "${item.feedId}")
                parentIntent.putExtra(ARG_FEEDTITLE, item.feedDisplayTitle)
                parentIntent.putExtra(ARG_FEED_URL, item.feedUrl.toString())
            }
            stackBuilder.getPendingIntent(item.id.toInt(), PendingIntent.FLAG_UPDATE_CURRENT)
        }
    }

    val builder = notificationBuilder(context)

    builder.setContentText(text)
            .setContentTitle(title)
            .setContentIntent(contentIntent)
            .setDeleteIntent(getPendingDeleteIntent(context, item))
            .setNumber(1)

    // Note that notifications must use PNG resources, because there is no compatibility for vector drawables here

    item.enclosureLink?.let { enclosureLink ->
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(enclosureLink))
        intent.putExtra(EXTRA_CREATE_NEW_TAB, true)
        builder.addAction(R.drawable.notification_play_circle_outline,
                context.getString(R.string.open_enclosed_media),
                PendingIntent.getActivity(context,
                        item.id.toInt(),
                        getOpenInDefaultActivityIntent(context, item.id, enclosureLink),
                        PendingIntent.FLAG_UPDATE_CURRENT))
    }

    item.link?.let { link ->
        builder.addAction(R.drawable.notification_open_in_browser,
                context.getString(R.string.open_link_in_browser),
                PendingIntent.getActivity(context,
                        item.id.toInt(),
                        getOpenInDefaultActivityIntent(context, item.id, link),
                        PendingIntent.FLAG_UPDATE_CURRENT))
    }

    builder.addAction(R.drawable.notification_check,
            context.getString(R.string.mark_as_read),
            PendingIntent.getActivity(context,
                    item.id.toInt(),
                    getOpenInDefaultActivityIntent(context, item.id, link = null),
                    PendingIntent.FLAG_UPDATE_CURRENT))

    style.setBuilder(builder)
    return style.build()
}

internal fun getOpenInDefaultActivityIntent(context: Context, feedItemId: Long, link: String? = null): Intent =
        Intent(Intent.ACTION_VIEW,
                // Important to keep the URI different so PendingIntents don't collide
                URI_FEEDITEMS.buildUpon().appendPath("$feedItemId").also {
                    if (link != null) {
                        it.appendQueryParameter(COL_LINK, link)
                    }
                }.build(),
                context,
                OpenLinkInDefaultActivity::class.java)

/**
 * Use this on platforms older than 24 to bundle notifications together
 */
private fun inboxNotification(context: Context, feedItems: List<FeedItemWithFeed>): Notification {
    val style = NotificationCompat.InboxStyle()
    val title = context.getString(R.string.updated_feeds)
    val text = feedItems.map { it.feedDisplayTitle }.toSet().joinToString(separator = ", ")

    style.setBigContentTitle(title)
    feedItems.forEach {
        style.addLine("${it.feedDisplayTitle} \u2014 ${it.plainTitle}")
    }

    val intent = Intent(context, FeedActivity::class.java)
    intent.putExtra(EXTRA_FEEDITEMS_TO_MARK_AS_NOTIFIED, LongArray(feedItems.size) { i -> feedItems[i].id })

    // We can be a little bit smart - if all items are from the same feed then go to that feed
    // Otherwise we should go to All feeds
    val feedIds = feedItems.map { it.feedId }.toSet()
    intent.data = if (feedIds.toSet().size == 1) {
        Uri.withAppendedPath(URI_FEEDS, "${feedIds.first()}")
    } else {
        Uri.withAppendedPath(URI_FEEDS, "-1")
    }

    val builder = notificationBuilder(context)

    builder.setContentText(text)
            .setContentTitle(title)
            .setContentIntent(PendingIntent.getActivity(context, notificationId, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT))
            .setDeleteIntent(getDeleteIntent(context, feedItems))
            .setNumber(feedItems.size)

    style.setBuilder(builder)
    return style.build()
}

private fun getDeleteIntent(context: Context, feedItems: List<FeedItemWithFeed>): PendingIntent {
    val intent = Intent(context, RssNotificationBroadcastReceiver::class.java)
    intent.action = ACTION_MARK_AS_NOTIFIED

    val ids = LongArray(feedItems.size) { i -> feedItems[i].id }
    intent.putExtra(EXTRA_FEEDITEM_ID_ARRAY, ids)

    return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
}

internal fun getDeleteIntent(context: Context, feedItem: FeedItemWithFeed): Intent {
    val intent = Intent(context, RssNotificationBroadcastReceiver::class.java)
    intent.action = ACTION_MARK_AS_NOTIFIED
    intent.data = Uri.withAppendedPath(URI_FEEDITEMS, "${feedItem.id}")
    val ids: LongArray = longArrayOf(feedItem.id)
    intent.putExtra(EXTRA_FEEDITEM_ID_ARRAY, ids)

    return intent
}

private fun getPendingDeleteIntent(context: Context, feedItem: FeedItemWithFeed): PendingIntent =
        PendingIntent.getBroadcast(context, 0, getDeleteIntent(context, feedItem), PendingIntent.FLAG_UPDATE_CURRENT)


private fun notificationBuilder(context: Context): NotificationCompat.Builder {
    val bm = BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)

    return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_f)
            .setLargeIcon(bm)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setPriority(NotificationCompat.PRIORITY_LOW)
}

private fun getItemsToNotify(context: Context): List<FeedItemWithFeed> {
    val feeds = getFeedIdsToNotify(context)

    return when (feeds.isEmpty()) {
        true -> emptyList()
        false -> AppDatabase.getInstance(context).feedItemDao().loadItemsToNotify(feeds)
    }
}

private fun getFeedIdsToNotify(context: Context): List<Long> =
        AppDatabase.getInstance(context).feedDao().loadFeedIdsToNotify()
