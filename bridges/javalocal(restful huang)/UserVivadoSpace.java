package com.jn.openhec.controller;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.URI;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import com.jn.openhec.persistent.AbstractDao;
import com.jn.openhec.persistent.AbstractObject;
import com.jn.openhec.rm.rmservice.RMNodeDBService;
import com.jn.openhec.common.util.DateUtil;
import com.jn.openhec.common.util.FileUtils;
import com.jn.openhec.dbservice.UserService;
import com.jn.openhec.dbservice.VivadoService;
import com.jn.openhec.entity.VivadoSpace;
import com.jn.openhec.entity.enums.FileOperationMode;
import com.jn.openhec.entity.sys.SysUser;
import com.jn.openhec.exception.EDUSDBException;

/**
 * @author huangxiaomin   黄丽民

 这是在项目中实际使用的时候修改的后台代码
 此代码适合rest接口

 *
 */
@RestController
@RequestMapping("/api/vivado")
public class UserVivadoSpaceController {
	@Autowired
	AbstractDao<AbstractObject, Serializable> dao;
	@Autowired
	private UserService userService;

	@Autowired
	private VivadoService vivadoService;

	private SysUser getCurrentUser() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		Object principal = authentication.getPrincipal();
		UserDetails userDetails = (UserDetails) principal;
		SysUser user = userService.findUserByUserId(userDetails.getUsername());
		return user;
	}

	@PreAuthorize("hasRole('USER')")
	@RequestMapping("createUserspace")
	public @ResponseBody JSONObject createUserspace() throws Exception {
		JSONObject result = new JSONObject();
		SysUser user = getCurrentUser();
		VivadoSpace vivadoSpace = vivadoService.findUserVivadoSpaceByUser(user);
		if (vivadoSpace == null) {
			VivadoSpace userVivadoSpace = new VivadoSpace();
			userVivadoSpace.setUser(user);
			userVivadoSpace.setCreate_time(new Date());
			userVivadoSpace.setIs_deleteed(false);
			vivadoService.addUserVivadoSpace(userVivadoSpace);
		} else {
			vivadoService.updateUserVivadoSpace(vivadoSpace);
		}

		result.put("errorflag", 1);
		result.put("errormsg", "Success");
		return result;
	}

	@PreAuthorize("hasRole('USER')")
	@RequestMapping("fileOperation")
	public @ResponseBody JSONObject fileOperation(@RequestBody JSONObject data) throws Exception {
		SysUser user = getCurrentUser();
		if (user == null) {
			throw new EDUSDBException("msg.user.notExist");
		} else {
			VivadoSpace vivadoSpace = vivadoService.findUserVivadoSpaceByUser(user);
			if (vivadoSpace.isIs_deleteed() == false) {
				String vivadospacePath = vivadoSpace.getVivadospace_workpath();
				FileOperationMode mode = FileOperationMode.valueOf(data.getString("action"));
				switch (mode) {
				case list:
					return foList(vivadospacePath, data);
				case getContent:
					return foGetContent(vivadospacePath, data);
				case createFolder:
					return foCreateFolder(vivadospacePath, data);
				case remove:
					return foRemove(vivadospacePath, data);
				case rename:
					return foRename(vivadospacePath, data);
				case move:
					return foMove(vivadospacePath, data);
				case edit:
					return foEdit(vivadospacePath, data);
				case copy:
					return foCopy(vivadospacePath, data);
				case compress:
					return foCompress(vivadospacePath, data);
				default:
					throw new EDUSDBException("not implemented");
				}

			} else {
				throw new EDUSDBException("msg.operationNotAllowed");
			}
		}
	}

	@PreAuthorize("hasRole('USER')")
	@RequestMapping(value = "uploadFile", method = RequestMethod.POST)
	public @ResponseBody JSONObject upload(HttpServletRequest request, HttpServletResponse response) throws Exception {
		SysUser user = getCurrentUser();
		if (user == null) {
			throw new EDUSDBException("msg.user.notExist");
		} else {
			MultipartHttpServletRequest multipartRequest = (MultipartHttpServletRequest) request;
			String dest = multipartRequest.getParameter("destination");
			VivadoSpace vivadoSpace = vivadoService.findUserVivadoSpaceByUser(user);
			if (vivadoSpace.isIs_deleteed() == false) {
				String vivadospacePath = vivadoSpace.getVivadospace_workpath();
				Map<String, MultipartFile> fileMap = multipartRequest.getFileMap();
				try {
					for (Map.Entry<String, MultipartFile> entity : fileMap.entrySet()) {
						MultipartFile mf = entity.getValue();
						String fileName = mf.getOriginalFilename();
						File uploadFile = new File(vivadospacePath + "/" + dest + "/" + fileName);
						FileCopyUtils.copy(mf.getBytes(), uploadFile);
					}
				} catch (IOException e) {
					return error(e.getMessage());
				}
			} else {
				throw new EDUSDBException("msg.operationNotAllowed");
			}
			return success();
		}
	}

	@PreAuthorize("hasRole('USER')")
	@RequestMapping(value = "downloadFile", method = RequestMethod.POST)
	public @ResponseBody void exportExcel(HttpServletResponse response, @RequestBody JSONObject data) throws Exception {
		SysUser user = getCurrentUser();
		if (user == null) {
			throw new EDUSDBException("msg.user.notExist");
		} else {
			String path = data.getString("path");
			VivadoSpace vivadoSpace = vivadoService.findUserVivadoSpaceByUser(user);
			if (vivadoSpace.isIs_deleteed() == false) {
				String vivadospacePath = vivadoSpace.getVivadospace_workpath();
				File file = new File(vivadospacePath + "/" + path);
				response.setContentLength((int) file.length());
				response.setHeader("Content-Disposition",
						"attachment;filename=" + new String(file.getName().getBytes(), "iso-8859-1"));
				InputStream is = new FileInputStream(vivadospacePath + "/" + path);
				OutputStream out = response.getOutputStream();
				IOUtils.copy(is, out);
				out.flush();
			} else {
				throw new EDUSDBException("msg.operationNotAllowed");
			}
		}
	}

    //打包下载文件
	@PreAuthorize("hasRole('USER')")
	@RequestMapping(value = "downloadMultipleFile", method = RequestMethod.POST)
	public @ResponseBody void downloadMultipleFile(HttpServletResponse response, @RequestBody JSONObject data)
			throws Exception {
		SysUser user = getCurrentUser();
		if (user == null) {
			throw new EDUSDBException("msg.user.notExist");
		} else {
			JSONArray items = data.getJSONArray("items");
			String toFilename = data.getString("toFilename");
			VivadoSpace vivadoSpace = vivadoService.findUserVivadoSpaceByUser(user);
			if (vivadoSpace.isIs_deleteed() == false) {
				String vivadospacePath = vivadoSpace.getVivadospace_workpath();
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(baos))) {
					for (Object item : items) {
						Path path = Paths.get(vivadospacePath, item.toString());
						ZipEntry zipEntry = new ZipEntry(item.toString());
						zos.putNextEntry(zipEntry);
						byte buffer[] = new byte[2048];
						try (BufferedInputStream bis = new BufferedInputStream(Files.newInputStream(path))) {
							int bytesRead = 0;
							while ((bytesRead = bis.read(buffer)) != -1) {
								zos.write(buffer, 0, bytesRead);
							}
						} finally {
							zos.closeEntry();
						}
					}
				}
				response.setContentType("application/zip");
				response.setHeader("Content-Disposition", "attachment; filename=\"" + toFilename + "\"");
				BufferedOutputStream output = new BufferedOutputStream(response.getOutputStream());
				output.write(baos.toByteArray());
				output.flush();
			} else {
				throw new EDUSDBException("msg.operationNotAllowed");
			}
		}
	}

	private JSONObject foList(String vivadospacePath, JSONObject data) throws Exception {
		List<JSONObject> resultList = new ArrayList<JSONObject>();
		try (DirectoryStream<Path> directoryStream = Files
				.newDirectoryStream(Paths.get(vivadospacePath, data.getString("path")))) {
			for (Path pathObj : directoryStream) {
				BasicFileAttributes attrs = Files.readAttributes(pathObj, BasicFileAttributes.class);
				JSONObject el = new JSONObject();
				el.put("name", pathObj.getFileName().toString());
				el.put("date",
						DateUtil.date2String(new Date(attrs.lastModifiedTime().toMillis()), "yyyy-MM-dd HH:mm:ss"));
				el.put("size", attrs.size());
				el.put("type", attrs.isDirectory() ? "dir" : "file");
				resultList.add(el);
			}
			directoryStream.close();
			JSONObject ret = new JSONObject();
			ret.put("result", resultList);
			return ret;
		} catch (Exception e) {
			return error(e.getMessage());
		}
	}

	private JSONObject foGetContent(String vivadospacePath, JSONObject data) throws Exception {
		try {
			String path = data.getString("item");
			String content = FileUtils.getFileContent(vivadospacePath + "/" + path);
			JSONObject jsonObject = new JSONObject();
			jsonObject.put("result", content);

			return jsonObject;
		} catch (Exception e) {
			return error(e.getMessage());
		}
	}

	private JSONObject foEdit(String vivadospacePath, JSONObject data) throws Exception {
		try {
			String path = data.getString("itemPath");
			String content = data.getString("content");

			File file = new File(vivadospacePath, path);
			org.apache.commons.io.FileUtils.writeStringToFile(file, content);
			return success();
		} catch (Exception e) {
			return error(e.getMessage());
		}
	}

	private JSONObject foCreateFolder(String vivadospacePath, JSONObject data) throws Exception {
		try {
			String path = data.getString("newPath");
			File newDir = new File(vivadospacePath, path);
			if (!newDir.mkdir()) {
				throw new Exception("Can't create directory: " + newDir.getAbsolutePath());
			}
			return success();
		} catch (Exception e) {
			return error(e.getMessage());
		}
	}

	private JSONObject foRemove(String vivadospacePath, JSONObject data) throws Exception {
		try {
			JSONArray items = data.getJSONArray("items");
			for (Object item : items) {
				String path = (String) item;
				File file = new File(vivadospacePath, path);
				if (!org.apache.commons.io.FileUtils.deleteQuietly(file)) {
					throw new Exception("Can't delete: " + path);
				}
			}
			return success();
		} catch (Exception e) {
			return error(e.getMessage());
		}
	}

	private JSONObject foRename(String vivadospacePath, JSONObject data) throws Exception {
		try {
			String item = data.getString("item");
			String newItem = data.getString("newItemPath");

			File srcFile = new File(vivadospacePath, item);
			File destFile = new File(vivadospacePath, newItem);
			if (srcFile.isFile()) {
				org.apache.commons.io.FileUtils.moveFile(srcFile, destFile);
			} else {
				org.apache.commons.io.FileUtils.moveDirectory(srcFile, destFile);
			}
			return success();
		} catch (Exception e) {
			return error(e.getMessage());
		}
	}

	private JSONObject foMove(String vivadospacePath, JSONObject data) throws Exception {
		try {
			JSONArray items = data.getJSONArray("items");
			String newPath = data.getString("newPath");

			for (Object item : items) {
				String path = (String) item;
				String newfilePath = newPath + "/" + path.substring(path.lastIndexOf("/"));
				File srcFile = new File(vivadospacePath, path);
				File destFile = new File(vivadospacePath, newfilePath);
				if (srcFile.isFile()) {
					org.apache.commons.io.FileUtils.moveFile(srcFile, destFile);
				} else {
					org.apache.commons.io.FileUtils.moveDirectory(srcFile, destFile);
				}
			}
			return success();
		} catch (Exception e) {
			return error(e.getMessage());
		}
	}

	private JSONObject foCopy(String vivadospacePath, JSONObject data) throws Exception {
		try {
			JSONArray items = data.getJSONArray("items");
			String newPath = data.getString("newPath");

			for (Object item : items) {
				String path = (String) item;
				String newfilePath = newPath + "/" + path.substring(path.lastIndexOf("/"));
				File srcFile = new File(vivadospacePath, path);
				File destFile = new File(vivadospacePath, newfilePath);
				if (srcFile.isFile()) {
					org.apache.commons.io.FileUtils.copyFile(srcFile, destFile);
				} else {
					org.apache.commons.io.FileUtils.copyDirectory(srcFile, destFile);
				}
			}
			return success();
		} catch (Exception e) {
			return error(e.getMessage());
		}
	}

    //压缩文件夹和文件   zip
	private JSONObject foCompress(String vivadospacePath, JSONObject data) throws Exception {
		try {
			JSONArray items = data.getJSONArray("items");
			String newPath = data.getString("destination");
			final Path dest = Paths.get(vivadospacePath, newPath);
			Path zip = dest.resolve(data.getString("compressedFilename")+".zip");
			if (Files.exists(zip)) {
				return error(zip.toString() + " already exits!");
			}
			Map<String, String> env = new HashMap<>();
			env.put("create", "true");
			boolean zipped = false;
			try (FileSystem zipfs = FileSystems.newFileSystem(URI.create("jar:file:" + zip.toString()), env)) {
				for (Object path : items) {
					Path realPath = Paths.get(vivadospacePath, path.toString());
					if (Files.isDirectory(realPath)) {
						Files.walkFileTree(Paths.get(vivadospacePath, path.toString()), new SimpleFileVisitor<Path>() {
							@Override
							public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
									throws IOException {
								Files.createDirectories(
										zipfs.getPath(dir.toString().substring(dest.toString().length())));
								return FileVisitResult.CONTINUE;
							}

							@Override
							public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
								Path pathInZipFile = zipfs.getPath(file.toString().substring(dest.toString().length()));
								Files.copy(file, pathInZipFile, StandardCopyOption.REPLACE_EXISTING);
								return FileVisitResult.CONTINUE;
							}

						});
					} else {
						Path pathInZipFile = zipfs.getPath("/",
								realPath.toString().substring(vivadospacePath.length() + newPath.length()));
						Path pathInZipFolder = pathInZipFile.getParent();
						if (!Files.isDirectory(pathInZipFolder)) {
							Files.createDirectories(pathInZipFolder);
						}
						Files.copy(realPath, pathInZipFile, StandardCopyOption.REPLACE_EXISTING);
					}
				}
				zipped = true;
			} finally {
				if (!zipped) {
					Files.deleteIfExists(zip);
				}
			}
			return success();
		} catch (IOException e) {
			return error(e.getClass().getSimpleName() + ":" + e.getMessage());
		}
	}

	private JSONObject success() {
		JSONObject result = new JSONObject();
		result.put("success", true);
		result.put("error", null);
		JSONObject ret = new JSONObject();
		ret.put("result", result);
		return ret;
	}

	private JSONObject error(String msg) {
		JSONObject result = new JSONObject();
		result.put("success", false);
		result.put("error", msg);
		JSONObject ret = new JSONObject();
		ret.put("result", result);
		return ret;
	}
	// =============================================================================================

	@MessageMapping("/active/{channelId}")
	public void operActive(@DestinationVariable String channelId) {
		System.out.println(channelId);
	}
}
