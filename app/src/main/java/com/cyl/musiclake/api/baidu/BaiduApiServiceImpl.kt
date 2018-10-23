package com.cyl.musiclake.api.baidu

import com.cyl.musicapi.baidu.BaiduApiService
import com.cyl.musicapi.playlist.MusicInfo
import com.cyl.musiclake.api.MusicUtils
import com.cyl.musiclake.api.MusicUtils.PIC_SIZE_BIG
import com.cyl.musiclake.api.MusicUtils.PIC_SIZE_NORMAL
import com.cyl.musiclake.api.MusicUtils.PIC_SIZE_SMALL
import com.cyl.musiclake.bean.Album
import com.cyl.musiclake.bean.Artist
import com.cyl.musiclake.bean.Music
import com.cyl.musiclake.bean.Playlist
import com.cyl.musiclake.common.Constants
import com.cyl.musiclake.bean.data.SongLoader
import com.cyl.musiclake.net.ApiManager
import com.cyl.musiclake.utils.FileUtils
import com.cyl.musiclake.utils.LogUtil
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import java.util.*

/**
 * Created by yonglong on 2018/1/21.
 */

object BaiduApiServiceImpl {
    private val TAG = "BaiduApiServiceImpl"
    private val apiService by lazy { ApiManager.getInstance().create(BaiduApiService::class.java, Constants.BASE_URL_BAIDU_MUSIC) }

    //    http://musicapi.qianqian.com/v1/restserver/ting?from=android&version=6.0.7.1&channel=huwei&operator=1&method=baidu.ting.billboard.billCategory&format=json&kflag=2

    //获取榜单
    fun getOnlinePlaylist(): Observable<MutableList<Playlist>> {
        val params = HashMap<String, String>()
        params[Constants.PARAM_METHOD] = Constants.METHOD_CATEGORY
        params["operator"] = "1"
        params["kflag"] = "2"
        params["format"] = "json"
        return apiService.getOnlinePlaylist(params).flatMap { it ->
            val playlists = mutableListOf<Playlist>()
            for (item in it.content!!) {
                val playlist = Playlist()
                playlist.name = item.name
                playlist.des = item.comment
                playlist.type = Constants.PLAYLIST_BD_ID
                playlist.pid = item.type.toString()
                playlist.coverUrl = item.picS192
                val musicList = mutableListOf<Music>()
                for (itemMusic in item.content!!) {
                    val music = Music()
                    music.title = itemMusic.title
                    music.album = itemMusic.albumTitle
                    music.artist = itemMusic.author
                    music.albumId = itemMusic.albumId
                    music.mid = itemMusic.songId
                    musicList.add(music)
                }
                playlist.musicList = musicList
                playlists.add(playlist)
            }
            Observable.create(ObservableOnSubscribe<MutableList<Playlist>> {
                it.onNext(playlists)
                it.onComplete()
            })
        }
    }

    /**
     * 获取歌单歌曲
     */
    fun getOnlineSongs(type: String, limit: Int, mOffset: Int): Observable<MutableList<Music>> {
        val params = HashMap<String, String>()

        params[Constants.PARAM_METHOD] = Constants.METHOD_GET_MUSIC_LIST
        params[Constants.PARAM_TYPE] = type
        params[Constants.PARAM_SIZE] = limit.toString()
        params[Constants.PARAM_OFFSET] = mOffset.toString()

        return apiService.getOnlineSongs(params)
                .flatMap { baiduSongList ->
                    val musicList = mutableListOf<Music>()
                    for (songInfo in baiduSongList.songList!!) {
                        val music = Music()
                        music.type = Constants.BAIDU
                        music.isOnline = true
                        music.mid = songInfo.songId
                        music.album = songInfo.albumTitle
                        music.albumId = songInfo.albumId
                        music.artist = songInfo.artistName
                        music.artistId = songInfo.tingUid
                        music.title = songInfo.title
                        music.isOnline = true
                        music.coverUri = MusicUtils.getAlbumPic(songInfo.picSmall, Constants.BAIDU, PIC_SIZE_NORMAL)
                        music.coverSmall = MusicUtils.getAlbumPic(songInfo.picSmall, Constants.BAIDU, PIC_SIZE_SMALL)
                        music.coverBig = MusicUtils.getAlbumPic(songInfo.picSmall, Constants.BAIDU, PIC_SIZE_BIG)
                        musicList.add(music)
                    }
                    Observable.create(ObservableOnSubscribe<MutableList<Music>> { e ->
                        try {
                            e.onNext(musicList)
                            e.onComplete()
                        } catch (ep: Exception) {
                            e.onError(ep)
                        }
                    })
                }
    }

    /**
     * 搜索建议
     */
    fun getSearchSuggestion(query: String): Observable<List<String>> {
        val params = HashMap<String, String>()
        params[Constants.PARAM_METHOD] = Constants.METHOD_SEARCH_SUGGESTION
        params[Constants.PARAM_QUERY] = query
        return apiService.getSearchSuggestion(params)
                .flatMap {
                    Observable.create(ObservableOnSubscribe<List<String>> { e ->
                        try {
                            e.onNext(it.suggestionList!!)
                            e.onComplete()
                        } catch (error: Exception) {
                            e.onError(Throwable(error.message))
                        }
                    })
                }
    }

    /**
     * 通过百度搜索,获取MusicInfo
     */
    fun getSearchMusicInfo(query: String, limit: Int, offset: Int): Observable<MutableList<Music>> {
        val params = mutableMapOf(
                Constants.PARAM_QUERY to query,
                Constants.PARAM_PAGE_SIZE to limit,
                Constants.PARAM_PAGE_NO to offset
        )
        return apiService.getSearchMerge(params)
                .flatMap {
                    Observable.create(ObservableOnSubscribe<MutableList<Music>> { e ->
                        val musicList = mutableListOf<Music>()
                        try {
                            if (it.errorCode == 22000) {
                                it.result.songInfo.songList?.forEach { song ->
                                    val musicInfo = Music()
                                    musicInfo.mid = song.songId
                                    musicInfo.type = Constants.BAIDU
                                    musicInfo.title = song.title
                                    musicInfo.artist = song.author
                                    musicInfo.artistId = song.allArtistId
                                    musicInfo.album = song.albumTitle
                                    musicInfo.albumId = song.albumId
                                    musicInfo.coverUri = song.picSmall
                                    musicList.add(musicInfo)
                                }
                            }
                        } catch (error: Exception) {
                            e.onError(Throwable(error.message))
                        }
                        e.onNext(musicList)
                        e.onComplete()
                    })
                }
    }

    /**
     * 获取歌单详情
     * "http://music.baidu.com/data/music/links?songIds=$mid"
     */
    fun getTingSongInfo(music: Music): Observable<Music> {
        val url = Constants.URL_GET_SONG_INFO + music.mid
        return apiService.getTingSongInfo(url)
                .flatMap { data ->
                    val music = Music()
                    val songInfo = data.data.songList?.get(0)
                    songInfo?.let {
                        music.type = Constants.BAIDU
                        music.isOnline = true
                        music.mid = songInfo.songId.toString()
                        music.album = songInfo.albumName
                        music.albumId = songInfo.albumId.toString()
                        music.artistId = songInfo.artistId
                        music.artist = songInfo.artistName
                        music.title = songInfo.songName
                        music.uri = songInfo.songLink
                        music.fileSize = songInfo.size.toLong()
                        music.lyric = songInfo.lrcLink
                        music.coverSmall = MusicUtils.getAlbumPic(songInfo.songPicSmall, Constants.BAIDU, MusicUtils.PIC_SIZE_SMALL)
                        music.coverUri = MusicUtils.getAlbumPic(songInfo.songPicSmall, Constants.BAIDU, MusicUtils.PIC_SIZE_NORMAL)
                        music.coverBig = MusicUtils.getAlbumPic(songInfo.songPicSmall, Constants.BAIDU, MusicUtils.PIC_SIZE_BIG)

                    }
                    Observable.create(ObservableOnSubscribe<Music> { e ->
                        if (music.uri != null) {
                            SongLoader.updateMusic(music)
                            e.onNext(music)
                            e.onComplete()
                        } else {
                            e.onError(Throwable())
                        }
                    })
                }
    }

    /**
     * 获取歌词
     */
    fun getBaiduLyric(music: Music): Observable<String>? {
        //本地歌词路径
        val mLyricPath = FileUtils.getLrcDir() + music.title + "-" + music.artist + ".lrc"
        //网络歌词
        val mLyricUrl = music.lyric
        return if (FileUtils.exists(mLyricPath)) {
            Observable.create { emitter ->
                try {
                    val lyric = FileUtils.readFile(mLyricPath)
                    emitter.onNext(lyric)
                    emitter.onComplete()
                } catch (e: Exception) {
                    emitter.onError(e)
                }
            }
        } else if (mLyricUrl != null) {
            apiService.getBaiduLyric(mLyricUrl)
                    .flatMap { baiDuLyricInfo ->
                        val lyric = baiDuLyricInfo.string()
                        //保存文件
                        val save = FileUtils.writeText(mLyricPath, lyric)
                        LogUtil.e("保存网络歌词：$save")
                        Observable.fromArray(lyric)
                    }
        } else {
            null
        }
    }

    /**
     * 获取电台列表
     */
    /**
     * 搜索建议
     */
    fun getRadioChannel(): Observable<MutableList<Playlist>> {
        return apiService.getRadioChannels()
                .flatMap {
                    Observable.create(ObservableOnSubscribe<MutableList<Playlist>> { e ->
                        try {
                            val result = mutableListOf<Playlist>()
                            if (it.errorCode == 22000) {
                                it.result?.get(0)?.channellist?.let {
                                    it.forEach {
                                        val playlist = Playlist()
                                        playlist.name = it.name
                                        playlist.pid = it.chName
                                        playlist.coverUrl = it.thumb
                                        playlist.des = it.cateSname
                                        playlist.type = Constants.PLAYLIST_BD_ID
                                        result.add(playlist)
                                    }
                                }
                            }
                            e.onNext(result)
                            e.onComplete()
                        } catch (error: Exception) {
                            e.onError(Throwable(error.message))
                        }
                    })
                }
    }

    /**
     * 电台歌单列表
     */
    fun getRadioChannelInfo(playlist: Playlist): Observable<Playlist> {
        return apiService.getRadioChannelSongs(playlist.pid!!)
                .flatMap {
                    val songs = mutableListOf<Music>()
                    if (it.errorCode == 22000) {
                        it.result.songlist?.forEach {
                            if (it.songid != null) {
                                val music = Music()
                                music.type = Constants.BAIDU
                                music.title = it.title
                                music.artist = it.artist
                                music.artistId = it.artistId
                                music.mid = it.songid
                                music.coverUri = MusicUtils.getAlbumPic(it.thumb, Constants.BAIDU, PIC_SIZE_NORMAL)
                                music.coverSmall = MusicUtils.getAlbumPic(it.thumb, Constants.BAIDU, PIC_SIZE_SMALL)
                                music.coverBig = MusicUtils.getAlbumPic(it.thumb, Constants.BAIDU, PIC_SIZE_BIG)
                                songs.add(music)
                            }
                        }
                        playlist.musicList = songs
                    }
                    Observable.create(ObservableOnSubscribe<Playlist> { e ->
                        try {
                            e.onNext(playlist)
                            e.onComplete()
                        } catch (error: Exception) {
                            e.onError(Throwable(error.message))
                        }
                    })
                }
    }

    fun getArtistSongList(artistId: String, offset: Int): Observable<Artist> {
        val params = mutableMapOf(
                Constants.PARAM_TING_UID to artistId,
                Constants.PARAM_OFFSET to offset,
                Constants.PARAM_LIMIT to 20
        )
        return apiService.getArtistSongList(params)
                .flatMap {
                    val artist = Artist()
                    val songs = mutableListOf<Music>()
                    if (it.errorCode == 22000) {
                        it.songList?.forEach {
                            val music = Music()
                            music.type = Constants.BAIDU
                            music.title = it.title
                            music.artist = it.artistName
                            music.artistId = it.tingUid
                            music.album = it.albumTitle
                            music.albumId = it.albumId
                            music.isOnline = true
                            music.mid = it.songId
                            music.coverUri = MusicUtils.getAlbumPic(it.picSmall, Constants.BAIDU, PIC_SIZE_NORMAL)
                            music.coverSmall = MusicUtils.getAlbumPic(it.picSmall, Constants.BAIDU, PIC_SIZE_SMALL)
                            music.coverBig = MusicUtils.getAlbumPic(it.picSmall, Constants.BAIDU, PIC_SIZE_BIG)
                            songs.add(music)
                        }
                        artist.count = it.songNums
                        artist.songs = songs
                    }
                    Observable.create(ObservableOnSubscribe<Artist> { e ->
                        try {
                            e.onNext(artist)
                            e.onComplete()
                        } catch (error: Exception) {
                            e.onError(Throwable(error.message))
                        }
                    })
                }
    }

    fun getAlbumSongList(albumId: String): Observable<Album> {
        return apiService.getAlbumSongList(albumId)
                .flatMap {
                    val album = Album()
                    val songs = mutableListOf<Music>()
                    it.songlist?.forEach {
                        val music = Music()
                        music.type = Constants.BAIDU
                        music.title = it.title
                        music.artist = it.artistName
                        music.artistId = it.tingUid
                        music.album = it.albumTitle
                        music.albumId = it.albumId
                        music.isOnline = true
                        music.mid = it.songId
                        music.coverUri = MusicUtils.getAlbumPic(it.picSmall, Constants.BAIDU, PIC_SIZE_NORMAL)
                        music.coverSmall = MusicUtils.getAlbumPic(it.picSmall, Constants.BAIDU, PIC_SIZE_SMALL)
                        music.coverBig = MusicUtils.getAlbumPic(it.picSmall, Constants.BAIDU, PIC_SIZE_BIG)
                        songs.add(music)
                    }
                    album.count = it.songlist?.size ?: 0
                    album.albumId = it.albumInfo.albumId
                    album.name = it.albumInfo.title
                    album.artistId = it.albumInfo.artistTingUid
                    album.artistName = it.albumInfo.author
                    album.info = it.albumInfo.info
                    album.songs = songs
                    Observable.create(ObservableOnSubscribe<Album> { e ->
                        try {
                            e.onNext(album)
                            e.onComplete()
                        } catch (error: Exception) {
                            e.onError(Throwable(error.message))
                        }
                    })
                }
    }
}
