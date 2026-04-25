# 文件名: music_player.py
import pygame
import requests
import time
import io
import os
import json
from threading import Thread, Event
import sys

# --- 配置和全局变量 ---
API_BASE_URL = "http://154.21.200.81:3000"
COMMAND_FILE = "music_command.txt"
HEADERS = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.36',
    'Referer': 'https://music.163.com/'
}

# --- 线程和状态管理 ---
exit_event = Event()
last_command = None
playback_thread = None
current_playback_token = None
current_song_id = None
SONG_END_EVENT = pygame.USEREVENT + 1

def send_status_to_java(status, **kwargs):
    """向Java进程发送JSON状态消息。"""
    payload = {"status": status, "data": kwargs}
    try:
        # 使用 flush 确保 Java 端能立即收到
        sys.stdout.write(json.dumps(payload) + '\n')
        sys.stdout.flush()
    except Exception:
        pass

def music_playback_logic(content, token):
    """
    处理歌曲播放逻辑。
    content: 可能是歌曲ID (数字字符串) 或 直链 (http开头)
    token: 用于验证当前播放任务是否被新的任务打断
    """
    global current_playback_token

    # 初始化变量
    song_id = "unknown"
    song_name = "Unknown Song"
    artist_name = "Unknown Artist"
    cover_url = ""
    duration_ms = 0
    music_url = ""

    try:
        if token != current_playback_token: return

        # --- 核心修改：判断是 ID 还是 URL ---
        if content.startswith("http"):
            # [模式 A] Java 发送的是直链 (VIP/已解灰)
            music_url = content
            song_id = "DirectURL" # 标记为直链播放

            # 此时我们没有通过 API 获取元数据，但这没关系
            # Java 端已经有 currentSong 对象，UI 会正常显示
            # 我们只需要发送 PLAYING 状态让 Java 知道开始播放了即可

        else:
            # [模式 B] Java 发送的是 ID (游客/回退模式)
            song_id = content

            # 1. 获取歌曲详情 (为了元数据)
            try:
                detail_response = requests.get(f"{API_BASE_URL}/song/detail?ids={song_id}", headers=HEADERS, timeout=10)
                detail_response.raise_for_status()
                song_details = detail_response.json().get('songs', [{}])[0]

                song_name = song_details.get('name', 'Unknown Song')
                artist_name = song_details.get('ar', [{}])[0].get('name', 'Unknown Artist')
                cover_url = song_details.get('al', {}).get('picUrl', '')
                duration_ms = song_details.get('dt', 0)
            except Exception as e:
                send_status_to_java("LOG", message=f"Metadata fetch failed: {e}")

            # 2. 获取播放链接
            url_response = requests.get(f"{API_BASE_URL}/song/url/v1?id={song_id}&level=standard", headers=HEADERS, timeout=10)
            url_response.raise_for_status()
            url_data = url_response.json().get('data', [])[0]
            music_url = url_data.get('url')

        # --- 统一播放逻辑 ---

        if not (music_url and music_url.startswith('http')):
            raise ValueError("未能获取有效的音乐链接")

        # 下载音频流
        audio_response = requests.get(music_url, headers=HEADERS, stream=True, timeout=30)
        audio_response.raise_for_status()

        total_size = int(audio_response.headers.get('content-length', 0))
        downloaded_size = 0
        last_report_time = time.time()
        audio_stream = io.BytesIO()

        for chunk in audio_response.iter_content(chunk_size=8192):
            # 下载过程中检查是否被切歌
            if token != current_playback_token:
                send_status_to_java("DOWNLOAD_INTERRUPTED", song_id=song_id)
                return

            if chunk:
                audio_stream.write(chunk)
                downloaded_size += len(chunk)

                current_time = time.time()
                if current_time - last_report_time >= 0.1:
                    if total_size > 0:
                        percentage = (downloaded_size / total_size) * 100
                        send_status_to_java("DOWNLOAD_PROGRESS", percentage=percentage)
                    last_report_time = current_time

        audio_stream.seek(0)

        # 下载完成，再次检查是否被切歌
        if token != current_playback_token: return

        # 加载并播放
        pygame.mixer.music.load(audio_stream)
        pygame.mixer.music.play()

        send_status_to_java("PLAYING",
                            song_id=song_id,
                            name=song_name,
                            artist=artist_name,
                            coverUrl=cover_url,
                            duration=duration_ms)

        # 播放进度监控循环
        last_progress_report_time = time.time()
        while token == current_playback_token:
            if pygame.mixer.music.get_busy():
                current_time = time.time()
                if current_time - last_progress_report_time >= 1.0:
                    current_pos_ms = pygame.mixer.music.get_pos()
                    if current_pos_ms != -1:
                        send_status_to_java("PROGRESS_UPDATE", position_ms=current_pos_ms)
                    last_progress_report_time = current_time
            time.sleep(0.2)

    except Exception as e:
        send_status_to_java("ERROR", song_id=song_id, message=str(e))

def command_file_watcher():
    """监视命令文件并更新 last_command 变量。"""
    global last_command
    while not exit_event.is_set():
        try:
            if os.path.exists(COMMAND_FILE):
                # 使用 errors='ignore' 防止编码问题导致崩溃
                with open(COMMAND_FILE, 'r', encoding='utf-8', errors='ignore') as f:
                    command = f.read().strip()
                os.remove(COMMAND_FILE)
                if command:
                    last_command = command
        except Exception:
            pass
        time.sleep(0.1)

def main():
    """主应用循环。"""
    send_status_to_java("INITIALIZED")
    try:
        pygame.init()
        pygame.mixer.init()
        pygame.mixer.music.set_endevent(SONG_END_EVENT)
    except Exception as e:
        send_status_to_java("ERROR", message=f"Pygame Init Failed: {e}")
        return

    watcher = Thread(target=command_file_watcher, daemon=True)
    watcher.start()

    global last_command, playback_thread, current_playback_token, current_song_id

    while not exit_event.is_set():
        if last_command:
            command_to_process = last_command
            last_command = None
            try:
                if command_to_process.startswith("PLAY:"):
                    # 更新 token，标记之前的播放任务失效
                    current_playback_token = time.time()

                    # 在手动停止歌曲前，临时禁用结束事件，防止它错误地触发 "FINISHED" 消息
                    pygame.mixer.music.set_endevent()
                    pygame.mixer.music.stop()
                    # 停止后，立即重新启用结束事件
                    pygame.mixer.music.set_endevent(SONG_END_EVENT)

                    # 获取冒号后的内容 (URL 或 ID)
                    # split(..., 1) 确保只分割第一个冒号，保护 URL 中的 http://
                    content = command_to_process.split(":", 1)[1].strip()

                    # 设置 current_song_id，用于 FINISHED 事件
                    # 如果是 URL，我们用一个标记，如果是 ID，用 ID
                    if content.startswith("http"):
                        current_song_id = "DirectURL"
                    else:
                        current_song_id = content

                    playback_thread = Thread(target=music_playback_logic, args=(content, current_playback_token), daemon=True)
                    playback_thread.start()

                elif command_to_process == "PAUSE":
                    pygame.mixer.music.pause()
                    send_status_to_java("PAUSED")

                elif command_to_process == "RESUME":
                    pygame.mixer.music.unpause()
                    send_status_to_java("RESUMED")

                elif command_to_process == "STOP":
                    current_playback_token = None
                    current_song_id = None
                    pygame.mixer.music.stop()
                    send_status_to_java("STOPPED")

                elif command_to_process == "EXIT":
                    exit_event.set()
                    current_playback_token = None
                    current_song_id = None
                    pygame.mixer.music.stop()
            except Exception as e:
                send_status_to_java("ERROR", message=f"Command processing failed: {e}")

        # 处理 Pygame 事件 (主要是歌曲自然播放结束)
        for event in pygame.event.get():
            if event.type == SONG_END_EVENT:
                # 只有当当前有歌曲ID且没有被手动停止时才发送 FINISHED
                if current_song_id:
                    send_status_to_java("FINISHED", song_id=current_song_id)
                    current_song_id = None # 防止重复触发
            if event.type == pygame.QUIT:
                exit_event.set()

        time.sleep(0.1)

    pygame.quit()
    send_status_to_java("SHUTDOWN")

if __name__ == '__main__':
    main()