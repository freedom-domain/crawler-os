import type { Component } from 'vue'
import {
  Aim, Avatar, Bell, Calendar, Camera, ChatDotRound, ChatLineRound, ChatSquare,
  Clock, Coin, Collection, Compass,
  Connection, Cpu, DataAnalysis, DataBoard, DataLine, Delete, Document, DocumentCopy, Download,
  Edit, EditPen, Files, Film, Folder, FolderOpened, Goods, Grid, Guide, Histogram,
  HomeFilled, House, Iphone, Lightning, Link, List, Location, Lock, MagicStick,
  MapLocation, Menu, Message, Microphone, Monitor, Money, Notebook, Odometer,
  Operation, Opportunity, Picture, PieChart, Position, PriceTag, Present, Printer,
  Promotion, Reading, Refresh, Search,
  Service, Setting, Share, ShoppingCart, Switch, Tickets, Tools, TrendCharts,
  Upload, User, Van, VideoCamera, View, Wallet
} from '@element-plus/icons-vue'

export const menuIconCategories = [
  '常用', 'AI 智能', '系统', '用户', '内容', '数据', '沟通', '位置', '业务', '工具'
] as const

export const menuIconOptions: Array<{
  value: string
  label: string
  category: typeof menuIconCategories[number]
  icon: Component
}> = [
  { value: 'Odometer', label: '仪表盘', category: '常用', icon: Odometer },
  { value: 'House', label: '首页', category: '常用', icon: House },
  { value: 'HomeFilled', label: '工作台', category: '常用', icon: HomeFilled },
  { value: 'Grid', label: '应用', category: '常用', icon: Grid },
  { value: 'Menu', label: '菜单', category: '常用', icon: Menu },
  { value: 'MagicStick', label: 'AI 助手', category: 'AI 智能', icon: MagicStick },
  { value: 'ChatLineRound', label: 'AI 对话', category: 'AI 智能', icon: ChatLineRound },
  { value: 'ChatSquare', label: '智能问答', category: 'AI 智能', icon: ChatSquare },
  { value: 'Lightning', label: '智能生成', category: 'AI 智能', icon: Lightning },
  { value: 'Opportunity', label: '灵感创意', category: 'AI 智能', icon: Opportunity },
  { value: 'Aim', label: '智能识别', category: 'AI 智能', icon: Aim },
  { value: 'Microphone', label: '语音助手', category: 'AI 智能', icon: Microphone },
  { value: 'User', label: '用户', category: '用户', icon: User },
  { value: 'Avatar', label: '角色', category: '用户', icon: Avatar },
  { value: 'Lock', label: '权限', category: '系统', icon: Lock },
  { value: 'Setting', label: '设置', category: '系统', icon: Setting },
  { value: 'Operation', label: '系统管理', category: '系统', icon: Operation },
  { value: 'PriceTag', label: '标签', category: '内容', icon: PriceTag },
  { value: 'Connection', label: '连接', category: '系统', icon: Connection },
  { value: 'List', label: '列表', category: '内容', icon: List },
  { value: 'Search', label: '搜索', category: '常用', icon: Search },
  { value: 'Folder', label: '文件夹', category: '内容', icon: Folder },
  { value: 'FolderOpened', label: '打开的文件夹', category: '内容', icon: FolderOpened },
  { value: 'Document', label: '文档', category: '内容', icon: Document },
  { value: 'DocumentCopy', label: '复制文档', category: '内容', icon: DocumentCopy },
  { value: 'Files', label: '文件集合', category: '内容', icon: Files },
  { value: 'Collection', label: '集合', category: '内容', icon: Collection },
  { value: 'Notebook', label: '笔记本', category: '内容', icon: Notebook },
  { value: 'Reading', label: '阅读', category: '内容', icon: Reading },
  { value: 'Picture', label: '图片', category: '内容', icon: Picture },
  { value: 'Camera', label: '相机', category: '内容', icon: Camera },
  { value: 'VideoCamera', label: '视频', category: '内容', icon: VideoCamera },
  { value: 'Film', label: '媒体', category: '内容', icon: Film },
  { value: 'Monitor', label: '监控', category: '系统', icon: Monitor },
  { value: 'Cpu', label: '处理器', category: '系统', icon: Cpu },
  { value: 'DataBoard', label: '数据看板', category: '数据', icon: DataBoard },
  { value: 'DataAnalysis', label: '数据分析', category: '数据', icon: DataAnalysis },
  { value: 'DataLine', label: '数据趋势', category: '数据', icon: DataLine },
  { value: 'TrendCharts', label: '趋势图', category: '数据', icon: TrendCharts },
  { value: 'PieChart', label: '饼图', category: '数据', icon: PieChart },
  { value: 'Histogram', label: '柱状图', category: '数据', icon: Histogram },
  { value: 'Calendar', label: '日历', category: '工具', icon: Calendar },
  { value: 'Clock', label: '时间', category: '工具', icon: Clock },
  { value: 'Bell', label: '通知', category: '沟通', icon: Bell },
  { value: 'Message', label: '消息', category: '沟通', icon: Message },
  { value: 'ChatDotRound', label: '聊天', category: '沟通', icon: ChatDotRound },
  { value: 'Service', label: '服务', category: '沟通', icon: Service },
  { value: 'Location', label: '位置', category: '位置', icon: Location },
  { value: 'MapLocation', label: '地图', category: '位置', icon: MapLocation },
  { value: 'Compass', label: '指南针', category: '位置', icon: Compass },
  { value: 'Position', label: '定位', category: '位置', icon: Position },
  { value: 'Guide', label: '指引', category: '位置', icon: Guide },
  { value: 'Link', label: '链接', category: '工具', icon: Link },
  { value: 'Share', label: '分享', category: '沟通', icon: Share },
  { value: 'Refresh', label: '刷新', category: '工具', icon: Refresh },
  { value: 'Upload', label: '上传', category: '工具', icon: Upload },
  { value: 'Download', label: '下载', category: '工具', icon: Download },
  { value: 'Edit', label: '编辑', category: '工具', icon: Edit },
  { value: 'EditPen', label: '编辑笔', category: '工具', icon: EditPen },
  { value: 'Delete', label: '删除', category: '工具', icon: Delete },
  { value: 'View', label: '查看', category: '工具', icon: View },
  { value: 'Tickets', label: '票据', category: '业务', icon: Tickets },
  { value: 'Goods', label: '商品', category: '业务', icon: Goods },
  { value: 'ShoppingCart', label: '购物车', category: '业务', icon: ShoppingCart },
  { value: 'Van', label: '运输', category: '业务', icon: Van },
  { value: 'Present', label: '礼物', category: '业务', icon: Present },
  { value: 'Coin', label: '金币', category: '业务', icon: Coin },
  { value: 'Money', label: '金额', category: '业务', icon: Money },
  { value: 'Wallet', label: '钱包', category: '业务', icon: Wallet },
  { value: 'Printer', label: '打印', category: '工具', icon: Printer },
  { value: 'Iphone', label: '手机', category: '系统', icon: Iphone },
  { value: 'Switch', label: '切换', category: '系统', icon: Switch },
  { value: 'Promotion', label: '推广', category: '业务', icon: Promotion },
  { value: 'Tools', label: '工具', category: '工具', icon: Tools }
]

export const menuIconLibrary = Object.fromEntries(
  menuIconOptions.map(option => [option.value, option.icon])
) as Record<string, Component>
